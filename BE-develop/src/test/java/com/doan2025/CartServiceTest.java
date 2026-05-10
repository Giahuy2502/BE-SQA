package com.doan2025;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.CartItem;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.response.CartItemResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.CartItemRepository;
import com.doan2025.webtoeic.repository.CourseRepository;
import com.doan2025.webtoeic.repository.EnrollmentRepository;
import com.doan2025.webtoeic.repository.OrderDetailRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.CartItemServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private CartItemServiceImpl cartItemService;

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
                .price(499_000L)
                .isActive(true)
                .isDelete(false)
                .build();
    }

    // ---------------------------------------------------------------
    // TC-CART-001: Thêm khóa học mới vào giỏ
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-001: should_AddCourseToCart_When_CourseIsNew")
    void should_AddCourseToCart_When_CourseIsNew() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(cartItemRepository.existsByCourseAndUser(course, student)).thenReturn(false);
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(false);

        // Act
        cartItemService.addToCart(httpRequest, course.getId());

        // CheckDB: cartItemRepository.save phải được gọi 1 lần với đúng user/course
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository, times(1)).save(captor.capture());
        CartItem saved = captor.getValue();
        assertEquals(student, saved.getUser());
        assertEquals(course, saved.getCourse());

        // Rollback: mock-only, không thay đổi DB.
    }

    // ---------------------------------------------------------------
    // TC-CART-002: Thêm trùng khóa học đã có trong giỏ
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-002: should_NotAddCourse_When_CourseAlreadyInCart")
    void should_NotAddCourse_When_CourseAlreadyInCart() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(cartItemRepository.existsByCourseAndUser(course, student)).thenReturn(true);

        // Act + Assert: theo đặc tả "Khóa học đã có trong giỏ hàng."
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.addToCart(httpRequest, course.getId()));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.CART_ITEM, ex.getResponseObject());

        // CheckDB: tuyệt đối không lưu trùng
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ---------------------------------------------------------------
    // TC-CART-003: Thêm khóa học không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-003: should_ThrowException_When_CourseDoesNotExist")
    void should_ThrowException_When_CourseDoesNotExist() {
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.addToCart(httpRequest, 999L));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.COURSE, ex.getResponseObject());

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ---------------------------------------------------------------
    // TC-CART-004 (BUG-FINDER theo KH71):
    //   Spec: nếu khóa học CHỈ đang có order PENDING (chưa thanh toán) và CHƯA có
    //         trong giỏ hàng → vẫn cho phép thêm vào giỏ.
    //   Code thực tế: OrderDetailRepository#existsByUserAndCourse lọc
    //         status != CANCELLED → bao gồm cả PENDING + COMPLETED → bị block.
    //         (CartItemServiceImpl#addToCart throw EXISTED/ORDER ngay).
    //   System test KH71: FAILED ("Order already exists").
    //   ⇒ Test này theo SPEC, đang phơi bày bug ⇒ @Disabled chờ fix.
    //
    //   Hướng fix: tách method repo thành `existsCompletedByUserAndCourse`
    //   (chỉ COMPLETED), service dùng method mới khi check ở addToCart.
    // ---------------------------------------------------------------
    /**
     * BUG-FINDER KH71 — để FAIL có chủ đích.
     * Spec: chỉ block khi đã COMPLETED (sở hữu); PENDING phải cho thêm vào giỏ.
     * Code: existsByUserAndCourse lọc status != CANCELLED → block luôn cả PENDING.
     * ⇒ Stub mock theo "spec đã được sửa" sẽ rơi vào nhánh save và pass; nhưng để
     *   phơi bày bug thực tế, mình dùng stub đại diện code thật:
     *   existsByUserAndCourse = true (mô phỏng đơn PENDING) ⇒ code throw EXISTED/ORDER
     *   ⇒ verify(cartItemRepository, times(1)).save(...) sẽ FAIL.
     */
    @Test
    @DisplayName("TC-CART-004: should_AllowAddToCart_When_OrderIsOnlyPending")
    void should_AllowAddToCart_When_OrderIsOnlyPending() {
        // Arrange: mô phỏng tình huống KH71 — đã có đơn PENDING trong DB.
        // Code hiện tại: existsByUserAndCourse SQL lọc status != CANCELLED ⇒ trả TRUE
        //                 với cả PENDING + COMPLETED ⇒ luôn throw EXISTED/ORDER.
        // Spec KH71: PENDING vẫn cho phép thêm vào giỏ ⇒ phải save.
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(cartItemRepository.existsByCourseAndUser(course, student)).thenReturn(false);
        // Đại diện cho behavior ĐANG SAI: SQL hiện tại trả true cho cả PENDING.
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(true);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(false);

        // Act: theo spec, phải KHÔNG throw exception và phải save.
        // Bọc assertDoesNotThrow để Surefire báo Failure (kèm message) thay vì Error.
        assertDoesNotThrow(() -> cartItemService.addToCart(httpRequest, course.getId()),
                "Spec KH71: order ở PENDING vẫn phải cho phép thêm vào giỏ, "
                        + "code hiện đang throw EXISTED/ORDER do existsByUserAndCourse "
                        + "lọc status != CANCELLED.");

        // CheckDB: cart item phải được save (theo spec KH71).
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    // ---------------------------------------------------------------
    // TC-CART-005: Đã có đơn COMPLETED thì không cho thêm vào giỏ
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-005: should_NotAddCourse_When_OrderAlreadyCompleted")
    void should_NotAddCourse_When_OrderAlreadyCompleted() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(cartItemRepository.existsByCourseAndUser(course, student)).thenReturn(false);
        // Đại diện cho trạng thái đã mua/đơn hoàn tất (COMPLETED) => phải block.
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(true);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(false);

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.addToCart(httpRequest, course.getId()));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ORDER, ex.getResponseObject());

        // CheckDB
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }


    // ---------------------------------------------------------------
    // TC-CART-006: Học viên đã enroll cố thêm vào giỏ
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-006: should_NotAddCourse_When_AlreadyEnrolled")
    void should_NotAddCourse_When_AlreadyEnrolled() {
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(cartItemRepository.existsByCourseAndUser(course, student)).thenReturn(false);
        when(orderDetailRepository.existsByUserAndCourse(student.getEmail(), course.getId())).thenReturn(false);
        when(enrollmentRepository.existsByUserAndCourse(student, course)).thenReturn(true);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.addToCart(httpRequest, course.getId()));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.ENROLLMENT, ex.getResponseObject());

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ---------------------------------------------------------------
    // TC-CART-008: Học viên xóa cart item của chính mình
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-008: should_RemoveCartItem_When_UserIsOwner")
    void should_RemoveCartItem_When_UserIsOwner() {
        Long cartItemId = 50L;
        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemId);
        cartItem.setUser(student);
        cartItem.setCourse(course);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(cartItemRepository.findById(cartItemId)).thenReturn(Optional.of(cartItem));

        cartItemService.removeFromCart(httpRequest, cartItemId);

        // CheckDB: deleteById phải được gọi đúng 1 lần với id 50
        verify(cartItemRepository, times(1)).deleteById(cartItemId);
    }

    // ---------------------------------------------------------------
    // TC-CART-009: Học viên cố xóa cart item của người khác
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-009: should_ThrowException_When_RemoveCartItemOfOther")
    void should_ThrowException_When_RemoveCartItemOfOther() {
        Long cartItemId = 50L;
        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemId);
        cartItem.setUser(otherStudent); // thuộc người khác
        cartItem.setCourse(course);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(cartItemRepository.findById(cartItemId)).thenReturn(Optional.of(cartItem));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.removeFromCart(httpRequest, cartItemId));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB: tuyệt đối không xóa
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-CART-011: Lấy danh sách giỏ hàng
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-011: should_ReturnAllCartItemsOfCurrentUser")
    void should_ReturnAllCartItemsOfCurrentUser() {
        CartItem ci1 = new CartItem();
        ci1.setId(1L);
        ci1.setUser(student);
        ci1.setCourse(course);

        Course course2 = Course.builder().id(101L).title("Java Advanced").price(799_000L).build();
        CartItem ci2 = new CartItem();
        ci2.setId(2L);
        ci2.setUser(student);
        ci2.setCourse(course2);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(cartItemRepository.findByEmailUser(student.getEmail()))
                .thenReturn(Arrays.asList(ci1, ci2));
        CartItemResponse r1 = mock(CartItemResponse.class);
        CartItemResponse r2 = mock(CartItemResponse.class);
        when(convertUtil.convertCartItemToDto(eq(httpRequest), eq(ci1))).thenReturn(r1);
        when(convertUtil.convertCartItemToDto(eq(httpRequest), eq(ci2))).thenReturn(r2);

        // Act
        List<CartItemResponse> result = cartItemService.getInCart(httpRequest);

        // Assert: trả về đúng 2 item, theo đúng thứ tự, mỗi item đi qua convertUtil đúng 1 lần.
        assertEquals(2, result.size());
        assertSame(r1, result.get(0));
        assertSame(r2, result.get(1));

        // CheckDB: tìm kiếm phải gọi đúng email user hiện tại
        verify(cartItemRepository, times(1)).findByEmailUser(student.getEmail());
    }

    // ---------------------------------------------------------------
    // TC-CART-007: addToCart khi user không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-007: should_ThrowException_When_UserNotFoundInAddToCart")
    void should_ThrowException_When_UserNotFoundInAddToCart() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("unknown@learnez.vn");
        when(userRepository.findByEmail("unknown@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.addToCart(httpRequest, course.getId()));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(courseRepository, never()).findById(anyLong());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ---------------------------------------------------------------
    // TC-CART-010: removeFromCart khi cart item không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-010: should_ThrowException_When_CartItemNotFoundOnRemove")
    void should_ThrowException_When_CartItemNotFoundOnRemove() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> cartItemService.removeFromCart(httpRequest, 999L));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.CART_ITEM, ex.getResponseObject());

        // CheckDB
        verify(cartItemRepository, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-CART-012: getInCart trả danh sách rỗng
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-CART-012: should_ReturnEmptyList_When_CartHasNoItem")
    void should_ReturnEmptyList_When_CartHasNoItem() {
        // Arrange
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(student.getEmail());
        when(cartItemRepository.findByEmailUser(student.getEmail())).thenReturn(Collections.emptyList());

        // Act
        List<CartItemResponse> result = cartItemService.getInCart(httpRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(convertUtil, never()).convertCartItemToDto(any(), any());
    }
}
