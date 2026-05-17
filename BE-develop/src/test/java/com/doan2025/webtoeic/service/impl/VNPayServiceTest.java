package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.EStatusOrder;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.Enrollment;
import com.doan2025.webtoeic.domain.OrderDetail;
import com.doan2025.webtoeic.domain.Orders;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.PaymentResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.EnrollmentRepository;
import com.doan2025.webtoeic.repository.OrderDetailRepository;
import com.doan2025.webtoeic.repository.OrderRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.PaymentServiceImpl;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bộ unit test cho {@link PaymentServiceImpl} - Tích hợp thanh toán VNPay.
 *
 * <p>Bám đặc tả nghiệp vụ "Tích hợp thanh toán VNPay":
 * tạo URL thanh toán cho đơn PENDING, không tạo URL cho đơn đã PAID/FAILED/CANCELLED,
 * xử lý callback theo `vnp_ResponseCode`, tạo enrollment khi thanh toán thành công,
 * không tạo enrollment khi callback thất bại.</p>
 *
 * <p><b>Quan trọng:</b>
 * - KHÔNG gọi VNPay thật trong test, chỉ kiểm tra logic build URL và side-effect DB.
 * - Trạng thái nghiệp vụ "PAID" trong đặc tả được hệ thống lưu là {@link EStatusOrder#COMPLETED}.
 * - Các tham số `@Value` (BE/FE/SECRET_KEY/...) được set bằng {@link ReflectionTestUtils} vì
 *   {@link org.mockito.junit.jupiter.MockitoExtension} không xử lý chúng.</p>
 *
 * <p><b>Rollback:</b> mock-only, không có DB thật, không cần rollback.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VNPayServiceTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepository userRepository;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User student;
    private Course course;
    private Orders pendingOrder;
    private OrderDetail orderDetail;

    @BeforeEach
    void setUp() {
        // Inject các @Value field bằng ReflectionTestUtils (không có Spring context).
        ReflectionTestUtils.setField(paymentService, "BE", "http://localhost:8080/");
        ReflectionTestUtils.setField(paymentService, "FE", "http://localhost:3000");
        ReflectionTestUtils.setField(paymentService, "SECRET_KEY", "TEST_SECRET_KEY_123456");
        ReflectionTestUtils.setField(paymentService, "ORDER_TYPE", "other");
        ReflectionTestUtils.setField(paymentService, "VPN_COMMAND", "pay");
        ReflectionTestUtils.setField(paymentService, "VPN_VERSION", "2.1.0");
        ReflectionTestUtils.setField(paymentService, "VPN_PAY_URL", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "VPN_TMN_CODE", "TMNTEST01");

        student = new User();
        student.setId(20L);
        student.setEmail("student@learnez.vn");
        student.setRole(ERole.STUDENT);

        course = Course.builder().id(100L).title("Java Basic").price(500_000L).build();

        pendingOrder = Orders.builder()
                .id(700L)
                .totalAmount(500_000L)
                .status(EStatusOrder.PENDING)
                .user(student)
                .build();

        orderDetail = OrderDetail.builder()
                .id(800L)
                .course(course)
                .orders(pendingOrder)
                .priceAtPurchase(500_000L)
                .build();
    }

    private void stubAuthHeaderForStudent() {
        // Ngữ cảnh: token Bearer hợp lệ tương ứng với student.
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
    }

    // ---------------------------------------------------------------
    // TC-PAY-001: Tạo URL VNPay cho đơn PENDING của chính học viên
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-001: should_CreatePaymentUrl_When_OrderIsPendingAndOwner")
    void should_CreatePaymentUrl_When_OrderIsPendingAndOwner() {
        // Arrange
        stubAuthHeaderForStudent();
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
        when(orderDetailRepository.findByOrderId(pendingOrder.getId())).thenReturn(Optional.of(orderDetail));
        // CommonUtil.getIpAddress đọc từ request, mock trả về null cho mọi header => IP fallback.
        when(httpRequest.getHeader("X-FORWARDED-FOR")).thenReturn(null);

        // Act
        PaymentResponse response = paymentService.createVNPayPayment(pendingOrder.getId(), httpRequest);

        // Assert: response trả về URL hợp lệ, status SUCCESS theo nghiệp vụ
        assertNotNull(response, "Response không được null");
        assertEquals(Constants.SUCCESS, response.getStatus());
        assertNotNull(response.getURL());
        assertTrue(response.getURL().startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"),
                "URL phải bắt đầu bằng vnp_PayUrl đã cấu hình");
        assertTrue(response.getURL().contains("vnp_TxnRef="),
                "URL phải chứa vnp_TxnRef do hệ thống sinh");
        assertTrue(response.getURL().contains("vnp_Amount="),
                "URL phải chứa vnp_Amount theo đơn hàng");
        assertTrue(response.getURL().contains("&vnp_SecureHash="),
                "URL phải chứa chữ ký vnp_SecureHash do hệ thống ký");

        // CheckDB: KHÔNG cập nhật trạng thái đơn khi mới tạo URL thanh toán.
        verify(orderRepository, never()).save(any(Orders.class));
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    // ---------------------------------------------------------------
    // TC-PAY-002: Đơn đã PAID (COMPLETED)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-002: should_ThrowException_When_OrderAlreadyPaid")
    void should_ThrowException_When_OrderAlreadyPaid() {
        // Arrange
        stubAuthHeaderForStudent();
        Orders paid = Orders.builder()
                .id(701L)
                .totalAmount(500_000L)
                .status(EStatusOrder.COMPLETED) // PAID theo đặc tả
                .user(student)
                .build();
        when(orderRepository.findById(paid.getId())).thenReturn(Optional.of(paid));
        when(orderDetailRepository.findByOrderId(paid.getId())).thenReturn(Optional.of(orderDetail));

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(paid.getId(), httpRequest));
        assertEquals(ResponseCode.HAS_PAID, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB: không tạo URL/enrollment, không thay đổi DB
        verify(orderRepository, never()).save(any(Orders.class));
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    // ---------------------------------------------------------------
    // TC-PAY-003: Đơn không thuộc user hiện tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-003: should_ThrowException_When_OrderNotBelongToCurrentUser")
    void should_ThrowException_When_OrderNotBelongToCurrentUser() {
        // Arrange
        stubAuthHeaderForStudent();
        User stranger = new User();
        stranger.setId(99L);
        stranger.setEmail("stranger@learnez.vn");
        stranger.setRole(ERole.STUDENT);

        Orders strangerOrder = Orders.builder()
                .id(702L)
                .totalAmount(500_000L)
                .status(EStatusOrder.PENDING)
                .user(stranger)
                .build();
        when(orderRepository.findById(strangerOrder.getId())).thenReturn(Optional.of(strangerOrder));

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(strangerOrder.getId(), httpRequest));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB: không cập nhật gì
        verify(orderRepository, never()).save(any(Orders.class));
    }

    // ---------------------------------------------------------------
    // TC-PAY-004: Đơn không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-004: should_ThrowException_When_OrderNotFound")
    void should_ThrowException_When_OrderNotFound() {
        stubAuthHeaderForStudent();
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(999L, httpRequest));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB: không truy cập OrderDetail khi đơn không tồn tại
        verify(orderDetailRepository, never()).findByOrderId(any());
    }

    // ---------------------------------------------------------------
    // TC-PAY-005: Thiếu Authorization header
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-005: should_ThrowException_When_AuthorizationHeaderIsMissing")
    void should_ThrowException_When_AuthorizationHeaderIsMissing() {
        // Arrange
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(pendingOrder.getId(), httpRequest));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB: fail ở user lookup nên không được tìm order
        verify(orderRepository, never()).findById(any());
        verify(orderDetailRepository, never()).findByOrderId(any());
    }

    // ---------------------------------------------------------------
    // TC-PAY-006: Authorization sai format (không phải Bearer)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-006: should_ThrowException_When_AuthorizationHeaderHasInvalidFormat")
    void should_ThrowException_When_AuthorizationHeaderHasInvalidFormat() {
        // Arrange
        when(httpRequest.getHeader("Authorization")).thenReturn("Token abc");
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(pendingOrder.getId(), httpRequest));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------
    // TC-PAY-007: Không tìm thấy OrderDetail
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-007: should_ThrowException_When_OrderDetailNotFound")
    void should_ThrowException_When_OrderDetailNotFound() {
        // Arrange
        stubAuthHeaderForStudent();
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
        when(orderDetailRepository.findByOrderId(pendingOrder.getId())).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(pendingOrder.getId(), httpRequest));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());
    }

    // ---------------------------------------------------------------
    // TC-PAY-008: Amount âm
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-008: should_ThrowException_When_OrderAmountIsNegative")
    void should_ThrowException_When_OrderAmountIsNegative() {
        // Arrange
        stubAuthHeaderForStudent();
        Orders invalidAmountOrder = Orders.builder()
                .id(705L)
                .totalAmount(-1L)
                .status(EStatusOrder.PENDING)
                .user(student)
                .build();
        OrderDetail detail = OrderDetail.builder()
                .id(901L).orders(invalidAmountOrder).course(course).priceAtPurchase(500_000L).build();

        when(orderRepository.findById(invalidAmountOrder.getId())).thenReturn(Optional.of(invalidAmountOrder));
        when(orderDetailRepository.findByOrderId(invalidAmountOrder.getId())).thenReturn(Optional.of(detail));

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(invalidAmountOrder.getId(), httpRequest));
        assertEquals(ResponseCode.INVALID, ex.getResponseCode());
        assertEquals(ResponseObject.AMOUNT, ex.getResponseObject());
    }

    // ---------------------------------------------------------------
    // TC-PAY-009: Nhánh catch khi lỗi tạo secure hash/payment URL
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-009: should_ThrowNotSuccess_When_ExceptionOccursInHashingPhase")
    void should_ThrowNotSuccess_When_ExceptionOccursInHashingPhase() {
        // Arrange
        stubAuthHeaderForStudent();
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
        when(orderDetailRepository.findByOrderId(pendingOrder.getId())).thenReturn(Optional.of(orderDetail));
        when(httpRequest.getHeader("X-FORWARDED-FOR")).thenReturn(null);

        // Ép lỗi trong pha tạo hash (khối try-catch dòng 153-155).
        ReflectionTestUtils.setField(paymentService, "SECRET_KEY", null);

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.createVNPayPayment(pendingOrder.getId(), httpRequest));
        assertEquals(ResponseCode.NOT_SUCCESS, ex.getResponseCode());
        assertEquals(ResponseObject.PAYMENT, ex.getResponseObject());
    }

    // ---------------------------------------------------------------
    // TC-PAY-010: Callback thành công => COMPLETED + tạo Enrollment
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-010: should_MarkOrderCompletedAndCreateEnrollment_When_CallbackSuccess")
    void should_MarkOrderCompletedAndCreateEnrollment_When_CallbackSuccess() {
        // Arrange
        String txnRef = "20260101120000_" + pendingOrder.getId();
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn(txnRef);
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("00");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TRANSACTION_NO)).thenReturn("123456789");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_AMOUNT)).thenReturn("50000000");
        when(httpRequest.getParameter(Constants.PAYMENT.VPN_PAY_DATE)).thenReturn("20260101120010");

        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
        when(orderDetailRepository.findByOrderId(pendingOrder.getId())).thenReturn(Optional.of(orderDetail));

        // Act
        RedirectView redirect = paymentService.handleVNPayReturn(httpRequest);

        // Assert: redirect URL chứa status=success
        assertNotNull(redirect);
        assertNotNull(redirect.getUrl());
        assertTrue(redirect.getUrl().contains("status=success"),
                "Redirect URL phải có status=success khi vnp_ResponseCode=00");

        // CheckDB: order.status được cập nhật COMPLETED (PAID theo đặc tả)
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        assertEquals(EStatusOrder.COMPLETED, orderCaptor.getValue().getStatus(),
                "Theo đặc tả: callback 00 => đơn chuyển sang PAID (COMPLETED).");

        // CheckDB: tạo Enrollment với user và course tương ứng đơn hàng
        ArgumentCaptor<Enrollment> enrollCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository, times(1)).save(enrollCaptor.capture());
        Enrollment savedEnrollment = enrollCaptor.getValue();
        assertEquals(student, savedEnrollment.getUser());
        assertEquals(course, savedEnrollment.getCourse());
    }

    // ---------------------------------------------------------------
    // TC-PAY-011: Callback thất bại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-011: should_NotUpdateOrderAndNotCreateEnrollment_When_CallbackFails")
    void should_NotUpdateOrderAndNotCreateEnrollment_When_CallbackFails() {
        String txnRef = "20260101120000_" + pendingOrder.getId();
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn(txnRef);
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("07");

        // Act
        RedirectView redirect = paymentService.handleVNPayReturn(httpRequest);

        // Assert
        assertNotNull(redirect);
        assertTrue(redirect.getUrl().contains("status=fail"),
                "Redirect URL phải có status=fail khi vnp_ResponseCode != 00");

        // CheckDB: KHÔNG cập nhật trạng thái đơn, KHÔNG tạo enrollment.
        verify(orderRepository, never()).save(any(Orders.class));
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
        // Cũng không cần truy vấn order detail trong nhánh fail.
        verify(orderDetailRepository, never()).findByOrderId(any());
    }

    // ---------------------------------------------------------------
    // TC-PAY-012: Callback success nhưng đơn không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-012: should_ThrowException_When_CallbackSuccessButOrderNotFound")
    void should_ThrowException_When_CallbackSuccessButOrderNotFound() {
        String txnRef = "20260101120000_999"; // orderId 999 không tồn tại
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn(txnRef);
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("00");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TRANSACTION_NO)).thenReturn("123");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_AMOUNT)).thenReturn("50000000");
        when(httpRequest.getParameter(Constants.PAYMENT.VPN_PAY_DATE)).thenReturn("20260101120010");
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.handleVNPayReturn(httpRequest));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB: không tạo enrollment khi không tìm thấy đơn.
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    // ---------------------------------------------------------------
    // TC-PAY-013: Callback success nhưng thiếu OrderDetail
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-013: should_ThrowException_When_CallbackSuccessButOrderDetailNotFound")
    void should_ThrowException_When_CallbackSuccessButOrderDetailNotFound() {
        // Arrange
        String txnRef = "20260101120000_" + pendingOrder.getId();
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn(txnRef);
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("00");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TRANSACTION_NO)).thenReturn("123");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_AMOUNT)).thenReturn("50000000");
        when(httpRequest.getParameter(Constants.PAYMENT.VPN_PAY_DATE)).thenReturn("20260101120010");
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));
        when(orderDetailRepository.findByOrderId(pendingOrder.getId())).thenReturn(Optional.empty());

        // Act + Assert: cover line 180 (orElseThrow)
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> paymentService.handleVNPayReturn(httpRequest));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB: không tạo enrollment khi thiếu order detail
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    // ---------------------------------------------------------------
    // TC-PAY-014: Callback gửi lại cho đơn đã COMPLETED (PAID)
    //
    // BUG-FINDER theo đặc tả VNPay — để FAIL có chủ đích.
    // Spec: callback gửi lại cho đơn đã PAID không tạo enrollment trùng,
    //       không xử lý thanh toán lần hai.
    // Code thực tế: handleVNPayReturn KHÔNG check `order.status == COMPLETED`
    //       trước khi tạo enrollment ⇒ vẫn save Enrollment ⇒ verify(...never())
    //       FAIL với message rõ.
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-PAY-014: should_NotCreateDuplicateEnrollment_When_CallbackResentForPaidOrder")
    void should_NotCreateDuplicateEnrollment_When_CallbackResentForPaidOrder() {
        // Theo đặc tả: callback gửi lại cho đơn đã PAID không được xử lý lần hai
        // và không tạo trùng enrollment.
        Orders paid = Orders.builder()
                .id(704L)
                .totalAmount(500_000L)
                .status(EStatusOrder.COMPLETED)
                .user(student)
                .build();
        OrderDetail paidDetail = OrderDetail.builder()
                .id(900L).course(course).orders(paid).priceAtPurchase(500_000L).build();

        String txnRef = "20260101120000_" + paid.getId();
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TXN_REF)).thenReturn(txnRef);
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_RESPONSE_CODE)).thenReturn("00");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_TRANSACTION_NO)).thenReturn("123");
        when(httpRequest.getParameter(Constants.PAYMENT.VNP_AMOUNT)).thenReturn("50000000");
        when(httpRequest.getParameter(Constants.PAYMENT.VPN_PAY_DATE)).thenReturn("20260101120010");
        when(orderRepository.findById(paid.getId())).thenReturn(Optional.of(paid));
        when(orderDetailRepository.findByOrderId(paid.getId())).thenReturn(Optional.of(paidDetail));
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(true);

        // Act
        paymentService.handleVNPayReturn(httpRequest);

        // CheckDB / Bug-finder: theo đặc tả KHÔNG được tạo enrollment trùng,
        // và KHÔNG xử lý thanh toán lần hai. Test này dùng để bộc lộ thiếu sót
        // nếu code chưa có guard `if (order.status == COMPLETED) return`.
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
        // Tùy đặc tả nghiệp vụ, đơn đã PAID không được "save" thêm lần nữa.
        verify(orderRepository, never()).save(any(Orders.class));
    }
}
