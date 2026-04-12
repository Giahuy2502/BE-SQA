package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.EStatusOrder;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.OrderDetail;
import com.doan2025.webtoeic.domain.Orders;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.PaymentResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.EnrollmentRepository;
import com.doan2025.webtoeic.repository.OrderDetailRepository;
import com.doan2025.webtoeic.repository.OrderRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentServiceImpl}. Externalized properties are set through {@link ReflectionTestUtils}.
 * <p>
 * Each {@code @Test} documents {@code Test Case ID}, behavior, {@code CheckDB}, and {@code Rollback} explicitly.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderDetailRepository orderDetailRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User payer;
    private Orders order;
    private Course course;

    @BeforeEach
    void injectConfigAndData() {
        ReflectionTestUtils.setField(paymentService, "BE", "http://localhost:8080/");
        ReflectionTestUtils.setField(paymentService, "FE", "http://localhost:4200");
        ReflectionTestUtils.setField(paymentService, "SECRET_KEY", "unit-test-secret-key");
        ReflectionTestUtils.setField(paymentService, "ORDER_TYPE", "other");
        ReflectionTestUtils.setField(paymentService, "VPN_COMMAND", "pay");
        ReflectionTestUtils.setField(paymentService, "VPN_VERSION", "2.1.0");
        ReflectionTestUtils.setField(paymentService, "VPN_PAY_URL", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "VPN_TMN_CODE", "TMN001");

        payer = new User();
        payer.setId(1L);
        payer.setEmail("payer@test.com");

        course = Course.builder().id(10L).title("Course A").build();

        order = Orders.builder()
                .id(55L)
                .user(payer)
                .status(EStatusOrder.PENDING)
                .totalAmount(100L)
                .build();
    }

    /**
     * Test Case ID: UTC-PAY-SVC-001
     * <p>
     * Without an {@code Authorization} bearer header the service resolves an empty email, fails user lookup, and must
     * not continue to load {@link Orders} (same entry as {@code GET /api/v1/payment/create}).
     * <p>
     * CheckDB: Verify {@link UserRepository#findByEmail} with empty string; {@link OrderRepository#findById} never runs.
     * Rollback: Not applicable — mocked repositories; no committed state.
     */
    @Test
    void createVNPayPayment_whenNoBearerHeader_throwsUserNotExisted() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn(null);
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, () -> paymentService.createVNPayPayment(55L, httpServletRequest));
        verify(orderRepository, never()).findById(anyLong());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-002
     * <p>
     * Valid bearer tokens still require a persisted {@link User}; otherwise {@link ResponseCode#NOT_EXISTED} fires.
     * <p>
     * CheckDB: {@link UserRepository#findByEmail} returns empty; {@link OrderRepository#save} never invoked.
     * Rollback: Not applicable — failure before writes.
     */
    @Test
    void createVNPayPayment_whenUserNotInDb_throws() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("missing@test.com");
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, () -> paymentService.createVNPayPayment(1L, httpServletRequest));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-003
     * <p>
     * Unknown {@code orderId} values must raise {@link ResponseCode#NOT_EXISTED} / {@link ResponseObject#ORDER}.
     * <p>
     * CheckDB: {@link OrderRepository#findById} optional empty; no {@code save}.
     * Rollback: Not applicable — exception path.
     */
    @Test
    void createVNPayPayment_whenOrderMissing_throws() {
        stubAuthorizedPayer();
        when(orderRepository.findById(55L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, () -> paymentService.createVNPayPayment(55L, httpServletRequest));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-004
     * <p>
     * Payers must match {@link Orders#getUser()}; mismatched emails trigger {@link ResponseCode#NOT_PERMISSION}.
     * <p>
     * CheckDB: No {@link OrderRepository#save} on forbidden access.
     * Rollback: Not applicable — authorization short-circuit.
     */
    @Test
    void createVNPayPayment_whenOrderBelongsToAnotherUser_throwsNotPermission() {
        stubAuthorizedPayer();
        User other = new User();
        other.setEmail("other@test.com");
        Orders foreign = Orders.builder().id(55L).user(other).status(EStatusOrder.PENDING).totalAmount(10L).build();
        when(orderRepository.findById(55L)).thenReturn(Optional.of(foreign));

        assertThrows(WebToeicException.class, () -> paymentService.createVNPayPayment(55L, httpServletRequest));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-005
     * <p>
     * VNPay payloads need {@link OrderDetail} (course title). Missing detail must throw {@link ResponseCode#NOT_EXISTED}.
     * <p>
     * CheckDB: {@link OrderDetailRepository#findByOrderId} returns empty; no persistence updates.
     * Rollback: Not applicable — failure before writes.
     */
    @Test
    void createVNPayPayment_whenOrderDetailMissing_throws() {
        stubAuthorizedPayer();
        when(orderRepository.findById(55L)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrderId(55L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, () -> paymentService.createVNPayPayment(55L, httpServletRequest));
    }

    /**
     * Test Case ID: UTC-PAY-SVC-006
     * <p>
     * Completed orders cannot initiate another VNPay session; expect {@link ResponseCode#HAS_PAID}.
     * <p>
     * CheckDB: Verify no {@link OrderRepository#save} during {@link PaymentServiceImpl#createVNPayPayment}.
     * Rollback: Not applicable — read-only guard.
     */
    @Test
    void createVNPayPayment_whenOrderAlreadyCompleted_throwsHasPaid() {
        stubAuthorizedPayer();
        Orders paid = Orders.builder()
                .id(55L).user(payer).status(EStatusOrder.COMPLETED).totalAmount(100L).build();
        when(orderRepository.findById(55L)).thenReturn(Optional.of(paid));
        OrderDetail detail = OrderDetail.builder().course(course).orders(paid).build();
        when(orderDetailRepository.findByOrderId(55L)).thenReturn(Optional.of(detail));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(55L, httpServletRequest));
        assertEquals(ResponseCode.HAS_PAID, ex.getResponseCode());
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-007
     * <p>
     * Negative {@link Orders#getTotalAmount()} values must be rejected with {@link ResponseCode#INVALID} /
     * {@link ResponseObject#AMOUNT}.
     * <p>
     * CheckDB: No repository writes on invalid monetary state.
     * Rollback: Not applicable — validation failure.
     */
    @Test
    void createVNPayPayment_whenAmountNegative_throwsInvalidAmount() {
        stubAuthorizedPayer();
        Orders badAmount = Orders.builder()
                .id(55L).user(payer).status(EStatusOrder.PENDING).totalAmount(-1L).build();
        when(orderRepository.findById(55L)).thenReturn(Optional.of(badAmount));
        when(orderDetailRepository.findByOrderId(55L))
                .thenReturn(Optional.of(OrderDetail.builder().course(course).orders(badAmount).build()));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(55L, httpServletRequest));
        assertEquals(ResponseCode.INVALID, ex.getResponseCode());
        assertEquals(ResponseObject.AMOUNT, ex.getResponseObject());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-008
     * <p>
     * Happy path builds a signed redirect URL: {@link PaymentResponse#getURL()} must include {@code vnp_SecureHash}.
     * <p>
     * CheckDB: Only read operations on {@link UserRepository}, {@link OrderRepository}, {@link OrderDetailRepository};
     * {@code createVNPayPayment} does not persist.
     * Rollback: Not applicable — no mutation in this method.
     */
    @Test
    void createVNPayPayment_whenValid_returnsPaymentUrl() {
        stubAuthorizedPayer();
        when(orderRepository.findById(55L)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrderId(55L))
                .thenReturn(Optional.of(OrderDetail.builder().course(course).orders(order).build()));
        when(httpServletRequest.getHeader("X-FORWARDED-FOR")).thenReturn("203.0.113.1");

        PaymentResponse response = paymentService.createVNPayPayment(55L, httpServletRequest);

        assertEquals(Constants.SUCCESS, response.getStatus());
        assertNotNull(response.getURL());
        assertTrue(response.getURL().startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(response.getURL().contains("vnp_SecureHash="));
    }

    /**
     * Test Case ID: UTC-PAY-SVC-009
     * <p>
     * Non-{@code 00} VNPay response codes should only redirect to the SPA with failure status—no persistence.
     * <p>
     * CheckDB: Verify neither {@link OrderRepository#save} nor {@link EnrollmentRepository#save} executes.
     * Rollback: Not applicable — gateway declined path.
     */
    @Test
    void handleVNPayReturn_whenResponseNotSuccess_doesNotPersistEnrollment() {
        when(httpServletRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn("20250101120000_55");
        when(httpServletRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("79");

        RedirectView view = paymentService.handleVNPayReturn(httpServletRequest);

        assertTrue(view.getUrl().contains("status=" + Constants.FAIL));
        verify(orderRepository, never()).save(any());
        verify(enrollmentRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-PAY-SVC-010
     * <p>
     * Successful callbacks mark {@link Orders} as {@link EStatusOrder#COMPLETED}, persist the change, insert
     * {@link com.doan2025.webtoeic.domain.Enrollment}, and redirect with success markers.
     * <p>
     * CheckDB: Verify {@link OrderRepository#save} and {@link EnrollmentRepository#save} with matching user/course.
     * Rollback: Not applicable in Mockito; production relies on service {@code @Transactional} for atomicity.
     */
    @Test
    void handleVNPayReturn_whenSuccess_updatesOrderAndEnrolls() {
        when(httpServletRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn("20250101120000_55");
        when(httpServletRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("00");
        when(httpServletRequest.getParameter(Constants.PAYMENT.VNP_TRANSACTION_NO)).thenReturn("TRX1");
        when(httpServletRequest.getParameter(Constants.PAYMENT.VNP_AMOUNT)).thenReturn("10000");
        when(httpServletRequest.getParameter(Constants.PAYMENT.VPN_PAY_DATE)).thenReturn("20250101120000");

        when(orderRepository.findById(55L)).thenReturn(Optional.of(order));
        OrderDetail detail = OrderDetail.builder().id(9L).orders(order).course(course).build();
        when(orderDetailRepository.findByOrderId(55L)).thenReturn(Optional.of(detail));

        RedirectView view = paymentService.handleVNPayReturn(httpServletRequest);

        assertTrue(view.getUrl().contains("status=" + Constants.SUCCESS));
        assertEquals(EStatusOrder.COMPLETED, order.getStatus());
        verify(orderRepository).save(order);
        verify(enrollmentRepository).save(argThat(en ->
                en.getUser().equals(payer) && en.getCourse().equals(course)));
    }

    private void stubAuthorizedPayer() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer x");
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(payer.getEmail());
        when(userRepository.findByEmail(payer.getEmail())).thenReturn(Optional.of(payer));
    }
}
