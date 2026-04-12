package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.EPaymentMethod;
import com.doan2025.webtoeic.constants.enums.EStatusOrder;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.*;
import com.doan2025.webtoeic.dto.SearchOrderDto;
import com.doan2025.webtoeic.dto.response.OrderResponse;
import com.doan2025.webtoeic.dto.response.StatisticOrderResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderServiceImpl} with Mockito (no real database).
 * <p>
 * Each {@code @Test} states {@code Test Case ID}, scenario details, explicit {@code CheckDB} and {@code Rollback} lines.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderDetailRepository orderDetailRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ConvertUtil convertUtil;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User buyer;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 5);
        buyer = new User();
        buyer.setId(1L);
        buyer.setEmail("buyer@test.com");
    }

    /**
     * Test Case ID: UTC-ORD-SVC-001
     * <p>
     * {@link OrderServiceImpl#getStatisticOrder} aggregates counts per status plus total purchases for the JWT email
     * (same data {@code GET /api/v1/order/statistic-orders} would rely on).
     * <p>
     * CheckDB: Verify {@link OrderRepository#countOrders} for null + each {@link EStatusOrder} and
     * {@link OrderRepository#totalPurchases} with the buyer email.
     * Rollback: Not applicable — mocked repositories only.
     */
    @Test
    void getStatisticOrder_buildsResponseFromRepositoryCounts() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(orderRepository.countOrders(isNull(), eq(buyer.getEmail()))).thenReturn(BigDecimal.valueOf(10));
        when(orderRepository.countOrders(eq(EStatusOrder.CANCELLED), eq(buyer.getEmail()))).thenReturn(BigDecimal.valueOf(1));
        when(orderRepository.countOrders(eq(EStatusOrder.COMPLETED), eq(buyer.getEmail()))).thenReturn(BigDecimal.valueOf(7));
        when(orderRepository.countOrders(eq(EStatusOrder.PENDING), eq(buyer.getEmail()))).thenReturn(BigDecimal.valueOf(2));
        when(orderRepository.totalPurchases(buyer.getEmail())).thenReturn(BigDecimal.valueOf(99));

        StatisticOrderResponse stats = orderService.getStatisticOrder(httpServletRequest);

        assertEquals(BigDecimal.valueOf(10), stats.getTotalOrders());
        assertEquals(BigDecimal.valueOf(1), stats.getCancelledOrders());
        assertEquals(BigDecimal.valueOf(7), stats.getCompletedOrders());
        assertEquals(BigDecimal.valueOf(2), stats.getPendingOrders());
        assertEquals(BigDecimal.valueOf(99), stats.getTotalPurchases());
        verify(orderRepository).countOrders(null, buyer.getEmail());
        verify(orderRepository).totalPurchases(buyer.getEmail());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-002
     * <p>
     * When {@link OrderRepository#findOwnOrders} returns an empty page, {@link OrderServiceImpl#getOwnOrders} must not
     * attempt to load {@link OrderDetail} rows (avoids useless DB chatter for {@code POST /api/v1/order}).
     * <p>
     * CheckDB: Verify {@link OrderDetailRepository#findByOrderId} never runs.
     * Rollback: Not applicable — no writes.
     */
    @Test
    void getOwnOrders_whenNoOrders_doesNotLoadOrderDetails() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(orderRepository.findOwnOrders(any(SearchOrderDto.class), eq(buyer.getEmail()), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<OrderResponse> page = orderService.getOwnOrders(httpServletRequest, new SearchOrderDto(), pageable);

        assertTrue(page.getContent().isEmpty());
        verify(orderDetailRepository, never()).findByOrderId(anyLong());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-003
     * <p>
     * Missing {@link OrderDetail} for a listed {@link Orders} must raise {@link WebToeicException} with
     * {@link ResponseCode#NOT_EXISTED} / {@link ResponseObject#ORDER} because the DTO cannot be built.
     * <p>
     * CheckDB: Expect {@link OrderDetailRepository#findByOrderId}; never a successful persistence call in this path.
     * Rollback: Not applicable — exception before writes.
     */
    @Test
    void getOwnOrders_whenOrderDetailMissing_throws() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        Orders order = Orders.builder().id(50L).user(buyer).build();
        when(orderRepository.findOwnOrders(any(), eq(buyer.getEmail()), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderDetailRepository.findByOrderId(50L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> orderService.getOwnOrders(httpServletRequest, new SearchOrderDto(), pageable));
        verify(orderDetailRepository).findByOrderId(50L);
    }

    /**
     * Test Case ID: UTC-ORD-SVC-004
     * <p>
     * {@link OrderServiceImpl#cancelOrder} must fail when {@link UserRepository#findByEmail} cannot resolve the JWT
     * identity (same guard as {@code POST /api/v1/order/delete}).
     * <p>
     * CheckDB: Verify {@code findByEmail}; {@link OrderRepository#save} must stay unused.
     * Rollback: Not applicable — no mutation.
     */
    @Test
    void cancelOrder_whenUserMissing_throws() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("x@test.com");
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> orderService.cancelOrder(httpServletRequest, List.of(1L)));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-005
     * <p>
     * Cancelling a non-existent order id must yield {@link ResponseCode#CANNOT_GET} / {@link ResponseObject#ORDER}.
     * <p>
     * CheckDB: {@link OrderRepository#findById} may run, but {@code save} must not.
     * Rollback: Not applicable — failure before persistence.
     */
    @Test
    void cancelOrder_whenOrderIdInvalid_throwsCannotGet() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.cancelOrder(httpServletRequest, List.of(999L)));
        assertEquals(ResponseCode.CANNOT_GET, ex.getResponseCode());
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-006
     * <p>
     * Students cannot cancel another person's order; expect {@link ResponseCode#NOT_PERMISSION} /
     * {@link ResponseObject#USER}.
     * <p>
     * CheckDB: Verify {@link OrderRepository#save} never invoked for foreign ownership.
     * Rollback: Not applicable — authorization failure before write.
     */
    @Test
    void cancelOrder_whenOrderBelongsToAnotherUser_throwsNotPermission() {
        User owner = new User();
        owner.setEmail("other@test.com");
        Orders order = Orders.builder().id(3L).user(owner).status(EStatusOrder.PENDING).build();
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        assertThrows(WebToeicException.class,
                () -> orderService.cancelOrder(httpServletRequest, List.of(3L)));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-007
     * <p>
     * Legitimate owner cancellation must flip {@link Orders#getStatus()} to {@link EStatusOrder#CANCELLED} and persist
     * through {@link OrderRepository#save}.
     * <p>
     * CheckDB: Verify {@code save} called with the mutated entity.
     * Rollback: Not applicable in Mockito tests; service {@code @Transactional} governs real rollbacks.
     */
    @Test
    void cancelOrder_whenOwner_success_savesCancelledStatus() {
        Orders order = Orders.builder().id(3L).user(buyer).status(EStatusOrder.PENDING).build();
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(httpServletRequest, List.of(3L));

        assertEquals(EStatusOrder.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
    }

    /**
     * Test Case ID: UTC-ORD-SVC-008
     * <p>
     * Unknown cart ids must raise {@link ResponseCode#CANNOT_GET} / {@link ResponseObject#CART_ITEM} before any order
     * row is created ({@code POST /api/v1/order/create-order-by-cart-item}).
     * <p>
     * CheckDB: Never {@link OrderRepository#save} nor {@link OrderDetailRepository#save}.
     * Rollback: Not applicable — failure before inserts.
     */
    @Test
    void createOrderByCartItem_whenCartMissing_throws() {
        when(cartItemRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpServletRequest, 7L));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-009
     * <p>
     * Duplicate purchases must be blocked when {@link OrderDetailRepository#existsByUserAndCourse} is true, yielding
     * {@link ResponseCode#EXISTED} / {@link ResponseObject#ORDER}.
     * <p>
     * CheckDB: Verify existence check; {@link OrderRepository#save} must not run.
     * Rollback: Not applicable — business rule short-circuit.
     */
    @Test
    void createOrderByCartItem_whenAlreadyOrdered_throwsExistedOrder() {
        Course course = Course.builder().id(100L).price(50L).title("TOEIC").build();
        CartItem cart = new CartItem();
        cart.setId(1L);
        cart.setCourse(course);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderDetailRepository.existsByUserAndCourse(buyer.getEmail(), 100L)).thenReturn(true);

        assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpServletRequest, 1L));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-010
     * <p>
     * If {@link EnrollmentRepository#existsByUserAndCourse} is true, creating another order must fail with
     * {@link ResponseCode#EXISTED} / {@link ResponseObject#ENROLLMENT}.
     * <p>
     * CheckDB: Verify enrollment existence probe; no new order persistence.
     * Rollback: Not applicable — guard clause only.
     */
    @Test
    void createOrderByCartItem_whenAlreadyEnrolled_throwsExistedEnrollment() {
        Course course = Course.builder().id(100L).price(50L).title("TOEIC").build();
        CartItem cart = new CartItem();
        cart.setId(1L);
        cart.setCourse(course);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderDetailRepository.existsByUserAndCourse(buyer.getEmail(), 100L)).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(buyer, course)).thenReturn(true);

        assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpServletRequest, 1L));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-011
     * <p>
     * Happy cart-checkout path persists {@link Orders} + {@link OrderDetail}, deletes the {@link CartItem}, and returns
     * the DTO from {@link ConvertUtil#convertOrderToDto}.
     * <p>
     * CheckDB: Verify {@link OrderRepository#save}, {@link OrderDetailRepository#save}, and
     * {@link CartItemRepository#deleteById}.
     * Rollback: Not applicable in unit tests; production transactional annotations handle atomicity/rollback.
     */
    @Test
    void createOrderByCartItem_whenValid_createsOrderDeletesCart() {
        Course course = Course.builder().id(100L).price(50L).title("TOEIC").build();
        CartItem cart = new CartItem();
        cart.setId(22L);
        cart.setCourse(course);
        when(cartItemRepository.findById(22L)).thenReturn(Optional.of(cart));
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderDetailRepository.existsByUserAndCourse(buyer.getEmail(), 100L)).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(buyer, course)).thenReturn(false);
        Orders savedOrder = Orders.builder().id(500L).user(buyer).totalAmount(50L)
                .status(EStatusOrder.PENDING).paymentMethod(EPaymentMethod.VN_PAY).build();
        when(orderRepository.save(any(Orders.class))).thenReturn(savedOrder);
        OrderDetail savedDetail = OrderDetail.builder().id(600L).orders(savedOrder).course(course).priceAtPurchase(50L).build();
        when(orderDetailRepository.save(any(OrderDetail.class))).thenReturn(savedDetail);
        OrderResponse dto = new OrderResponse();
        when(convertUtil.convertOrderToDto(eq(httpServletRequest), eq(savedOrder), eq(savedDetail))).thenReturn(dto);

        OrderResponse out = orderService.createOrderByCartItem(httpServletRequest, 22L);

        assertSame(dto, out);
        verify(orderRepository).save(argThat(o -> o.getTotalAmount().equals(50L)
                && o.getStatus() == EStatusOrder.PENDING
                && o.getPaymentMethod() == EPaymentMethod.VN_PAY));
        verify(orderDetailRepository).save(any(OrderDetail.class));
        verify(cartItemRepository).deleteById(22L);
    }

    /**
     * Test Case ID: UTC-ORD-SVC-012
     * <p>
     * Unknown course ids must fail with {@link ResponseCode#NOT_EXISTED} / {@link ResponseObject#COURSE}.
     * <p>
     * CheckDB: Only {@link CourseRepository#findById} should execute; never order persistence.
     * Rollback: Not applicable — exception before writes.
     */
    @Test
    void createOrderByCourseId_whenCourseMissing_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpServletRequest, 1L));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-013
     * <p>
     * Direct-buy flow must reject duplicates detected by {@link OrderDetailRepository#existsByUserAndCourse}.
     * <p>
     * CheckDB: Verify duplicate probe; {@link OrderRepository#save} absent.
     * Rollback: Not applicable — duplicate guard.
     */
    @Test
    void createOrderByCourseId_whenAlreadyOrdered_throws() {
        Course course = Course.builder().id(200L).price(120L).build();
        when(courseRepository.findById(200L)).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderDetailRepository.existsByUserAndCourse(buyer.getEmail(), 200L)).thenReturn(true);

        assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpServletRequest, 200L));
        verify(orderRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-ORD-SVC-014
     * <p>
     * Successful direct purchase persists order + detail and never touches the cart repository.
     * <p>
     * CheckDB: Verify {@link OrderRepository#save} and {@link OrderDetailRepository#save}; cart delete not invoked.
     * Rollback: Not applicable in Mockito scope; service transactions cover real consistency.
     */
    @Test
    void createOrderByCourseId_whenValid_savesOrderAndDetail() {
        Course course = Course.builder().id(200L).price(120L).title("Listening").build();
        when(courseRepository.findById(200L)).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(buyer.getEmail());
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderDetailRepository.existsByUserAndCourse(buyer.getEmail(), 200L)).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(buyer, course)).thenReturn(false);
        Orders savedOrder = Orders.builder().id(700L).user(buyer).totalAmount(120L)
                .status(EStatusOrder.PENDING).paymentMethod(EPaymentMethod.VN_PAY).build();
        when(orderRepository.save(any(Orders.class))).thenReturn(savedOrder);
        OrderDetail detail = OrderDetail.builder().id(800L).orders(savedOrder).course(course).priceAtPurchase(120L).build();
        when(orderDetailRepository.save(any(OrderDetail.class))).thenReturn(detail);
        OrderResponse dto = new OrderResponse();
        when(convertUtil.convertOrderToDto(eq(httpServletRequest), eq(savedOrder), eq(detail))).thenReturn(dto);

        OrderResponse out = orderService.createOrderByCourseID(httpServletRequest, 200L);

        assertSame(dto, out);
        verify(orderRepository).save(any(Orders.class));
        verify(orderDetailRepository).save(any(OrderDetail.class));
        verify(cartItemRepository, never()).deleteById(anyLong());
    }
}
