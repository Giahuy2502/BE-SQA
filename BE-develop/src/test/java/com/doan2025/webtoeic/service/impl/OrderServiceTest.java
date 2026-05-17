package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.EPaymentMethod;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.EStatusOrder;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.CartItem;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.OrderDetail;
import com.doan2025.webtoeic.domain.Orders;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.StatisticOrderResponse;
import com.doan2025.webtoeic.dto.response.OrderResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.CartItemRepository;
import com.doan2025.webtoeic.repository.CourseRepository;
import com.doan2025.webtoeic.repository.EnrollmentRepository;
import com.doan2025.webtoeic.repository.OrderDetailRepository;
import com.doan2025.webtoeic.repository.OrderRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.OrderServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.doan2025.webtoeic.dto.SearchOrderDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bộ unit test cho {@link OrderServiceImpl} - Quản lý đơn hàng.
 *
 * <p>Bám đặc tả nghiệp vụ "Quản lý đơn hàng":
 * tạo đơn từ cart item / mua ngay theo courseId, hủy đơn của chính mình,
 * trạng thái khởi tạo PENDING, không cho mua lại course đã sở hữu.</p>
 *
 * <p><b>Rollback:</b> mock-only, không cần rollback DB thật.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User student;
    private User otherStudent;
    private Course course;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(20L);
        student.setEmail("student@learnez.vn");
        student.setRole(ERole.STUDENT);

        otherStudent = new User();
        otherStudent.setId(21L);
        otherStudent.setEmail("other@learnez.vn");
        otherStudent.setRole(ERole.STUDENT);

        course = Course.builder()
                .id(100L)
                .title("Java Basic")
                .price(500_000L)
                .isActive(true)
                .isDelete(false)
                .build();
    }

    // ---------------------------------------------------------------
    // TC-ORDER-005: Tạo đơn từ cart item (happy path)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-005: should_CreateOrderWithStatusPending_When_CreateFromCartItem")
    void should_CreateOrderWithStatusPending_When_CreateFromCartItem() {
        // Arrange
        Long cartItemId = 10L;
        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemId);
        cartItem.setUser(student);
        cartItem.setCourse(course);

        when(cartItemRepository.findById(cartItemId)).thenReturn(Optional.of(cartItem));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(false);
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders o = inv.getArgument(0);
            o.setId(500L);
            return o;
        });
        when(orderDetailRepository.save(any(OrderDetail.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertOrderToDto(eq(httpRequest), any(Orders.class), any(OrderDetail.class)))
                .thenReturn(mock(OrderResponse.class));

        // Act
        orderService.createOrderByCartItem(httpRequest, cartItemId);

        // CheckDB: kiểm tra Orders được lưu với status PENDING, total = course.price, paymentMethod=VN_PAY
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        Orders savedOrder = orderCaptor.getValue();
        assertEquals(EStatusOrder.PENDING, savedOrder.getStatus(),
                "Đơn hàng vừa tạo phải ở trạng thái PENDING theo đặc tả");
        assertEquals(EPaymentMethod.VN_PAY, savedOrder.getPaymentMethod());
        assertEquals(500_000L, savedOrder.getTotalAmount());
        assertEquals(student, savedOrder.getUser());

        // CheckDB: OrderDetail được lưu đúng course và priceAtPurchase
        ArgumentCaptor<OrderDetail> detailCaptor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(orderDetailRepository, times(1)).save(detailCaptor.capture());
        OrderDetail savedDetail = detailCaptor.getValue();
        assertEquals(course, savedDetail.getCourse());
        assertEquals(500_000L, savedDetail.getPriceAtPurchase());
        assertEquals(savedOrder, savedDetail.getOrders());

        // CheckDB: cart item phải được xóa khỏi giỏ sau khi tạo đơn
        verify(cartItemRepository, times(1)).deleteById(cartItemId);
    }

    // ---------------------------------------------------------------
    // TC-ORDER-006: Course đã có trong order khác chưa hủy
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-006: should_ThrowException_When_CourseAlreadyInAnotherOrder")
    void should_ThrowException_When_CourseAlreadyInAnotherOrder() {
        Long cartItemId = 10L;
        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemId);
        cartItem.setUser(student);
        cartItem.setCourse(course);

        when(cartItemRepository.findById(cartItemId)).thenReturn(Optional.of(cartItem));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(true);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpRequest, cartItemId));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB: không tạo đơn, không xóa cart item
        verify(orderRepository, never()).save(any(Orders.class));
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-ORDER-007: Chặn tạo đơn khi đã enroll
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-007: should_ThrowException_When_AlreadyEnrolled")
    void should_ThrowException_When_AlreadyEnrolled() {
        Long cartItemId = 10L;
        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemId);
        cartItem.setUser(student);
        cartItem.setCourse(course);

        when(cartItemRepository.findById(cartItemId)).thenReturn(Optional.of(cartItem));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(true);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpRequest, cartItemId));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ENROLLMENT, ex.getResponseObject());

        verify(orderRepository, never()).save(any(Orders.class));
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-ORDER-008: Tạo đơn từ cart item nhưng cart item không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-008: should_ThrowException_When_CartItemNotFound")
    void should_ThrowException_When_CartItemNotFound() {
        // Arrange
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpRequest, 999L));
        assertEquals(ResponseCode.CANNOT_GET, ex.getResponseCode());
        assertEquals(ResponseObject.CART_ITEM, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).save(any(Orders.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-ORDER-009: Tạo đơn từ cart item nhưng user không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-009: should_ThrowException_When_UserNotFoundInCreateOrderByCartItem")
    void should_ThrowException_When_UserNotFoundInCreateOrderByCartItem() {
        // Arrange
        Long cartItemId = 10L;
        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemId);
        cartItem.setUser(student);
        cartItem.setCourse(course);

        when(cartItemRepository.findById(cartItemId)).thenReturn(Optional.of(cartItem));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("unknown@learnez.vn");
        when(userRepository.findByEmail("unknown@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCartItem(httpRequest, cartItemId));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).save(any(Orders.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-ORDER-010: Mua ngay theo courseId
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-010: should_CreateOrder_When_BuyNowByCourseId")
    void should_CreateOrder_When_BuyNowByCourseId() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(false);
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders o = inv.getArgument(0);
            o.setId(501L);
            return o;
        });
        when(orderDetailRepository.save(any(OrderDetail.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertOrderToDto(eq(httpRequest), any(Orders.class), any(OrderDetail.class)))
                .thenReturn(mock(OrderResponse.class));

        orderService.createOrderByCourseID(httpRequest, course.getId());

        // CheckDB: tạo Orders đúng đặc tả
        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository, times(1)).save(captor.capture());
        assertEquals(EStatusOrder.PENDING, captor.getValue().getStatus());
        assertEquals(course.getPrice(), captor.getValue().getTotalAmount());

        // Khác với createOrderByCartItem: KHÔNG có cart item nào để xóa
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-ORDER-011: Mua ngay với courseId không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-011: should_ThrowException_When_BuyNowCourseNotFound")
    void should_ThrowException_When_BuyNowCourseNotFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpRequest, 999L));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.COURSE, ex.getResponseObject());

        verify(orderRepository, never()).save(any(Orders.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-013: Buy now nhưng user không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-013: should_ThrowException_When_UserNotFoundInBuyNow")
    void should_ThrowException_When_UserNotFoundInBuyNow() {
        // Arrange
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("unknown@learnez.vn");
        when(userRepository.findByEmail("unknown@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpRequest, course.getId()));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).save(any(Orders.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-014: Buy now nhưng đã có order trước đó
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-014: should_ThrowException_When_BuyNowCourseAlreadyInOrder")
    void should_ThrowException_When_BuyNowCourseAlreadyInOrder() {
        // Arrange
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(true);

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpRequest, course.getId()));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).save(any(Orders.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-015: Buy now nhưng user đã enrollment
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-015: should_ThrowException_When_BuyNowCourseAlreadyEnrolled")
    void should_ThrowException_When_BuyNowCourseAlreadyEnrolled() {
        // Arrange
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(true);

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpRequest, course.getId()));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ENROLLMENT, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).save(any(Orders.class));
        verify(orderDetailRepository, never()).save(any(OrderDetail.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-001: Hủy đơn của chính học viên
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-001: should_CancelOrders_When_UserIsOwner")
    void should_CancelOrders_When_UserIsOwner() {
        Orders o1 = Orders.builder().id(1L).user(student).status(EStatusOrder.PENDING).build();
        Orders o2 = Orders.builder().id(2L).user(student).status(EStatusOrder.PENDING).build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o1));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(o2));
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancelOrder(httpRequest, Arrays.asList(1L, 2L));

        // CheckDB: orderRepository.save phải được gọi 2 lần với status=CANCELLED
        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository, times(2)).save(captor.capture());
        for (Orders o : captor.getAllValues()) {
            assertEquals(EStatusOrder.CANCELLED, o.getStatus(),
                    "Đơn bị hủy phải chuyển sang trạng thái CANCELLED theo đặc tả");
        }
    }

    // ---------------------------------------------------------------
    // TC-ORDER-002: Hủy đơn của người khác
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-002: should_ThrowException_When_CancelOrderOfOther")
    void should_ThrowException_When_CancelOrderOfOther() {
        Orders o1 = Orders.builder().id(1L).user(otherStudent).status(EStatusOrder.PENDING).build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o1));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.cancelOrder(httpRequest, Collections.singletonList(1L)));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        verify(orderRepository, never()).save(any(Orders.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-003: cancelOrder khi user không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-003: should_ThrowException_When_UserNotFoundInCancelOrder")
    void should_ThrowException_When_UserNotFoundInCancelOrder() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("unknown@learnez.vn");
        when(userRepository.findByEmail("unknown@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.cancelOrder(httpRequest, Collections.singletonList(1L)));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Orders.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-004: cancelOrder khi không tìm thấy order
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-004: should_ThrowException_When_OrderNotFoundInCancelOrder")
    void should_ThrowException_When_OrderNotFoundInCancelOrder() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.cancelOrder(httpRequest, Collections.singletonList(999L)));
        assertEquals(ResponseCode.CANNOT_GET, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB
        verify(orderRepository, never()).save(any(Orders.class));
    }

    // ---------------------------------------------------------------
    // TC-ORDER-018: thống kê đơn hàng theo trạng thái + tổng chi tiêu
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-018: should_ReturnStatisticOrder_When_GetStatisticOrder")
    void should_ReturnStatisticOrder_When_GetStatisticOrder() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(orderRepository.countOrders(null, student.getEmail())).thenReturn(BigDecimal.valueOf(12));
        when(orderRepository.countOrders(EStatusOrder.CANCELLED, student.getEmail())).thenReturn(BigDecimal.valueOf(2));
        when(orderRepository.countOrders(EStatusOrder.COMPLETED, student.getEmail())).thenReturn(BigDecimal.valueOf(7));
        when(orderRepository.countOrders(EStatusOrder.PENDING, student.getEmail())).thenReturn(BigDecimal.valueOf(3));
        when(orderRepository.totalPurchases(student.getEmail())).thenReturn(BigDecimal.valueOf(2_500_000L));

        // Act
        StatisticOrderResponse result = orderService.getStatisticOrder(httpRequest);

        // Assert
        assertEquals(BigDecimal.valueOf(12), result.getTotalOrders());
        assertEquals(BigDecimal.valueOf(2), result.getCancelledOrders());
        assertEquals(BigDecimal.valueOf(7), result.getCompletedOrders());
        assertEquals(BigDecimal.valueOf(3), result.getPendingOrders());
        assertEquals(BigDecimal.valueOf(2_500_000L), result.getTotalPurchases());

        // CheckDB
        verify(orderRepository, times(1)).countOrders(null, student.getEmail());
        verify(orderRepository, times(1)).countOrders(EStatusOrder.CANCELLED, student.getEmail());
        verify(orderRepository, times(1)).countOrders(EStatusOrder.COMPLETED, student.getEmail());
        verify(orderRepository, times(1)).countOrders(EStatusOrder.PENDING, student.getEmail());
        verify(orderRepository, times(1)).totalPurchases(student.getEmail());
    }

    // ---------------------------------------------------------------
    // TC-ORDER-016: getOwnOrders happy path mapping nhiều phần tử
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-016: should_MapOrderResponses_When_GetOwnOrders")
    void should_MapOrderResponses_When_GetOwnOrders() {
        // Arrange
        SearchOrderDto dto = new SearchOrderDto();
        Pageable pageable = PageRequest.of(0, 10);

        Orders o1 = Orders.builder().id(1L).user(student).status(EStatusOrder.PENDING).build();
        Orders o2 = Orders.builder().id(2L).user(student).status(EStatusOrder.COMPLETED).build();
        Page<Orders> ordersPage = new PageImpl<>(List.of(o1, o2), pageable, 2);

        OrderDetail d1 = OrderDetail.builder().id(101L).orders(o1).course(course).priceAtPurchase(500_000L).build();
        OrderDetail d2 = OrderDetail.builder().id(102L).orders(o2).course(course).priceAtPurchase(500_000L).build();

        OrderResponse r1 = OrderResponse.builder().id(1L).status(EStatusOrder.PENDING).build();
        OrderResponse r2 = OrderResponse.builder().id(2L).status(EStatusOrder.COMPLETED).build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(orderRepository.findOwnOrders(dto, student.getEmail(), pageable)).thenReturn(ordersPage);
        when(orderDetailRepository.findByOrderId(1L)).thenReturn(Optional.of(d1));
        when(orderDetailRepository.findByOrderId(2L)).thenReturn(Optional.of(d2));
        when(convertUtil.convertOrderToDto(httpRequest, o1, d1)).thenReturn(r1);
        when(convertUtil.convertOrderToDto(httpRequest, o2, d2)).thenReturn(r2);

        // Act
        Page<OrderResponse> result = orderService.getOwnOrders(httpRequest, dto, pageable);

        // Assert
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getId());
        assertEquals(2L, result.getContent().get(1).getId());
        assertEquals(EStatusOrder.PENDING, result.getContent().get(0).getStatus());
        assertEquals(EStatusOrder.COMPLETED, result.getContent().get(1).getStatus());

        // CheckDB
        verify(orderRepository, times(1)).findOwnOrders(dto, student.getEmail(), pageable);
        verify(orderDetailRepository, times(1)).findByOrderId(1L);
        verify(orderDetailRepository, times(1)).findByOrderId(2L);
        verify(convertUtil, times(1)).convertOrderToDto(httpRequest, o1, d1);
        verify(convertUtil, times(1)).convertOrderToDto(httpRequest, o2, d2);
    }

    // ---------------------------------------------------------------
    // TC-ORDER-020: getOwnOrders khi thiếu OrderDetail của một order
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-ORDER-020: should_ThrowException_When_GetOwnOrdersMissingOrderDetail")
    void should_ThrowException_When_GetOwnOrdersMissingOrderDetail() {
        // Arrange
        SearchOrderDto dto = new SearchOrderDto();
        Pageable pageable = PageRequest.of(0, 10);
        Orders order = Orders.builder().id(1L).user(student).status(EStatusOrder.PENDING).build();
        Page<Orders> ordersPage = new PageImpl<>(List.of(order), pageable, 1);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(orderRepository.findOwnOrders(dto, student.getEmail(), pageable)).thenReturn(ordersPage);
        when(orderDetailRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.getOwnOrders(httpRequest, dto, pageable));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB: dừng tại orElseThrow, không gọi convert
        verify(orderDetailRepository, times(1)).findByOrderId(1L);
        verify(convertUtil, never()).convertOrderToDto(any(), any(), any());
    }

    // =====================================================================
    //  KH166 / KH174 / KH186 — Thanh toán tất cả: tạo 1 đơn chứa N OrderDetail
    //  Spec: nhấn [Thanh toán tất cả] tạo 1 Orders với status=PENDING,
    //        totalAmount = SUM(course.price), kèm N OrderDetail, đồng thời xóa
    //        toàn bộ cart item của user khỏi giỏ.
    //  Code thực tế: KHÔNG có method `createOrderFromAllCartItems(...)`.
    //        Hiện tại chỉ có `createOrderByCartItem(Long id)` xử lý đúng 1 cart item.
    //  System test KH166/KH174/KH186: FAILED — không có flow "Thanh toán tất cả".
    //
    //  Test này CHƯA gọi method (vì chưa tồn tại) ⇒ chỉ document yêu cầu.
    //  Khi dev bổ sung method, mở @Disabled này, gọi vào và assert.
    // =====================================================================
    /**
     * BUG-FINDER KH166/KH174/KH186 — để FAIL có chủ đích (không skip).
     * Spec: "Thanh toán tất cả" tạo 1 đơn chứa N OrderDetail từ giỏ.
     * Code: OrderService chưa có method `createOrderFromAllCartItems(...)`.
     *
     * Vì method chưa tồn tại nên không thể gọi để verify side-effect, mình dùng
     * fail() với reason rõ ràng để báo cáo lý do FAIL trong test report.
     * Khi dev bổ sung method, thay fail() bằng phần Act + Assert dưới.
     */
    @Test
    @DisplayName("TC-ORDER-019: should_CreateSingleOrderWithAllCartItems_When_CheckoutAll")
    void should_CreateSingleOrderWithAllCartItems_When_CheckoutAll() {
        fail("Spec gap KH166/KH174/KH186: OrderService thiếu method createOrderFromAllCartItems(httpRequest). "
                + "Khi bổ sung, method phải: (1) tạo 1 Orders status=PENDING, totalAmount = SUM(course.price); "
                + "(2) lưu N OrderDetail tương ứng; (3) deleteById toàn bộ cart item của user.");
    }

    @Test
    @DisplayName("TC-ORDER-012: should_CreateOnlyOneOrder_When_ClickBuyNowConcurrently")
    void should_CreateOnlyOneOrder_When_ClickBuyNowConcurrently() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

        // Lần 1 chưa có order; lần 2 thấy order vừa tạo nên phải bị chặn.
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId()))
                .thenReturn(false, true);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(false);
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders o = inv.getArgument(0);
            o.setId(System.currentTimeMillis());
            return o;
        });
        when(orderDetailRepository.save(any(OrderDetail.class))).thenAnswer(inv -> inv.getArgument(0));

        // Lần click đầu tiên tạo order thành công.
        orderService.createOrderByCourseID(httpRequest, course.getId());

        // Lần click thứ hai phải bị chặn vì course đã có trong order chưa hủy.
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> orderService.createOrderByCourseID(httpRequest, course.getId()));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // Chỉ lần click đầu được tạo Orders/OrderDetail.
        verify(orderRepository, times(1)).save(any(Orders.class));
        verify(orderDetailRepository, times(1)).save(any(OrderDetail.class));
    }

    // =====================================================================
    //  KH197 — Filter ngày: 30/04 → 01/04 phải swap thành 01/04 → 30/04
    //  Code thực tế: getOwnOrders truyền nguyên dto xuống repository, không swap.
    //  System test KH197: FAILED.
    // =====================================================================
    /**
     * BUG-FINDER KH197 — để FAIL có chủ đích.
     * Spec: filter ngày 30/04 → 01/04 phải swap thành 01/04 → 30/04 trước khi query.
     * Code: getOwnOrders chuyển nguyên dto xuống repository, không swap.
     * ⇒ assert fromDate <= toDate sẽ FAIL.
     */
    @Test
    @DisplayName("TC-ORDER-017: should_SwapFromDateAndToDate_When_GetOwnOrdersWithReversedRange")
    void should_SwapFromDateAndToDate_When_GetOwnOrdersWithReversedRange() {
        // Arrange: dto fromDate = 30/04, toDate = 01/04 (cùng năm)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(2026, java.util.Calendar.APRIL, 30, 0, 0, 0);
        java.util.Date later = cal.getTime();
        cal.set(2026, java.util.Calendar.APRIL, 1, 0, 0, 0);
        java.util.Date earlier = cal.getTime();

        SearchOrderDto dto = new SearchOrderDto();
        dto.setFromDate(later);
        dto.setToDate(earlier);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Orders> emptyPage = new PageImpl<>(Collections.emptyList());
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(orderRepository.findOwnOrders(any(SearchOrderDto.class), eq(student.getEmail()), eq(pageable)))
                .thenReturn(emptyPage);

        // Act
        orderService.getOwnOrders(httpRequest, dto, pageable);

        // CheckDB: dto truyền xuống repository phải có fromDate <= toDate
        ArgumentCaptor<SearchOrderDto> captor = ArgumentCaptor.forClass(SearchOrderDto.class);
        verify(orderRepository).findOwnOrders(captor.capture(), eq(student.getEmail()), eq(pageable));
        SearchOrderDto effective = captor.getValue();
        assertNotNull(effective.getFromDate());
        assertNotNull(effective.getToDate());
        assertTrue(!effective.getFromDate().after(effective.getToDate()),
                "Spec KH197: fromDate phải <= toDate sau khi service swap");
    }
}
