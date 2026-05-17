package com.doan2025.webtoeic.service.impl;


import com.doan2025.webtoeic.constants.enums.ECategoryCourse;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.request.CourseRequest;
import com.doan2025.webtoeic.dto.response.CourseResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.CourseRepository;
import com.doan2025.webtoeic.repository.EnrollmentRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.CourseServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import com.doan2025.webtoeic.utils.NotiUtils;
import jakarta.servlet.http.HttpServletRequest;
import com.doan2025.webtoeic.dto.SearchBaseDto;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bộ unit test cho {@link CourseServiceImpl} thuộc module Khóa học online.
 *
 * <p>Bám đặc tả nghiệp vụ "Quản lý khóa học":
 * tạo / cập nhật / xóa mềm khóa học, kiểm tra phân quyền MANAGER và validate
 * các trường bắt buộc (title, price, categoryId, authorId).</p>
 *
 * <p><b>Rollback:</b> đây là unit test thuần Mockito, mọi repository đều là mock,
 * không có DB thật => không cần cơ chế rollback vật lý. Mọi thao tác "save"
 * trong test chỉ là mô phỏng và không làm thay đổi database.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Cho phép stub không bị tiêu thụ ở các nhánh exception sớm.
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private NotiUtils notiUtils;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private CourseServiceImpl courseService;

    private User manager;          // Người đăng nhập đóng vai MANAGER
    private User consultant;       // Người đăng nhập đóng vai CONSULTANT 
    private User author;           // Tác giả của khóa học
    private Course existingCourse; // Một khóa học đã tồn tại trong DB (mock)

    /*
     * Note:
     * - Tác dụng: khởi tạo dữ liệu nền dùng chung cho toàn bộ test, gồm MANAGER,
     *   CONSULTANT, AUTHOR và một course mẫu đã tồn tại.
     * - Kết quả/trả về: hàm setup là `void`, không trả dữ liệu; trạng thái trả về
     *   được thể hiện qua các field instance đã được gán giá trị trước mỗi test.
     * - Kỹ thuật test: dùng lifecycle `@BeforeEach` của JUnit 5 để reset fixture,
     *   giúp mỗi test độc lập và không phụ thuộc thứ tự chạy.
     */
    @BeforeEach
    void setUp() {
        // Arrange chung: dữ liệu user và course mặc định, có thể tái sử dụng cho nhiều test.
        // (User entity không có @Builder, dùng setter để build object trong test.)
        manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@learnez.vn");
        manager.setRole(ERole.MANAGER);

        consultant = new User();
        consultant.setId(2L);
        consultant.setEmail("consultant@learnez.vn");
        consultant.setRole(ERole.CONSULTANT);

        author = new User();
        author.setId(10L);
        author.setEmail("author@learnez.vn");
        author.setRole(ERole.CONSULTANT);

        existingCourse = Course.builder()
                .id(100L)
                .title("Java Basic")
                .description("Khóa nền tảng")
                .price(499_000L)
                .thumbnailUrl("img.png")
                .categoryCourse(ECategoryCourse.fromValue(1))
                .author(author)
                .createdBy(manager)
                .isActive(true)
                .isDelete(false)
                .build();
    }

    // ---------------------------------------------------------------
    // TC-COURSE-001: Tạo khóa học hợp lệ
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra luồng createCourse thành công khi request có đầy đủ
     *   title, description, price, authorId, categoryId và thumbnail.
     * - Kết quả/trả về: test là `void`; service phải trả về đúng CourseResponse
     *   do convertUtil tạo ra, đồng thời entity được save có đầy đủ field nghiệp vụ.
     * - Kỹ thuật test: mock JWT/user/author/repository, dùng `thenAnswer` để mô phỏng
     *   JPA save, dùng `ArgumentCaptor<Course>` để kiểm tra dữ liệu trước khi lưu.
     */
    @Test
    @DisplayName("TC-COURSE-001: should_CreateCourse_When_RequestIsValid")
    void should_CreateCourse_When_RequestIsValid() {
        // Arrange: chuẩn bị request hợp lệ và mock các phụ thuộc
        CourseRequest request = new CourseRequest();
        request.setTitle("Java Basic");
        request.setDescription("Khóa nền tảng");
        request.setPrice(499_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);
        request.setThumbnailUrl("img.png");

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findUserOnlyStudent()).thenReturn(Collections.emptyList());

        // Mô phỏng JPA: khi save thì trả về Course có isActive=true, isDelete=false (như sau @PrePersist).
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(101L);
            c.setIsActive(true);
            c.setIsDelete(false);
            return c;
        });
        CourseResponse expectedResponse = mock(CourseResponse.class);
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(expectedResponse);

        // Act
        CourseResponse actual = courseService.createCourse(httpRequest, request);

        // Assert: kiểm tra đối tượng trả về và DB access
        assertSame(expectedResponse, actual, "Phải trả về đúng CourseResponse do convertUtil tạo ra");

        // CheckDB: courseRepository.save phải được gọi đúng 1 lần với đầy đủ field theo đặc tả.
        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(1)).save(courseCaptor.capture());
        Course saved = courseCaptor.getValue();
        assertEquals("Java Basic", saved.getTitle());
        assertEquals("Khóa nền tảng", saved.getDescription());
        assertEquals(499_000L, saved.getPrice());
        assertEquals("img.png", saved.getThumbnailUrl());
        assertEquals(ECategoryCourse.fromValue(1), saved.getCategoryCourse());
        assertEquals(author, saved.getAuthor());
        assertEquals(manager, saved.getCreatedBy());
        assertTrue(saved.getIsActive(), "Sau persist, isActive phải là true theo @PrePersist");
        assertFalse(saved.getIsDelete(), "Sau persist, isDelete phải là false");

        // Rollback: không có DB thật, mọi thao tác chỉ trên mock.
    }

    // ---------------------------------------------------------------
    // TC-COURSE-002: Thiếu title
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: xác nhận createCourse chặn request không có title.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException với
     *   ResponseCode.IS_NULL và ResponseObject.TITLE, không trả CourseResponse.
     * - Kỹ thuật test: dùng `assertThrows` để bắt exception nghiệp vụ và `verify(..., never())`
     *   để bảo đảm repository không ghi dữ liệu khi validate fail.
     */
    @Test
    @DisplayName("TC-COURSE-002: should_ThrowException_When_TitleIsMissing")
    void should_ThrowException_When_TitleIsMissing() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setTitle(null);
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.TITLE, ex.getResponseObject());

        // CheckDB: tuyệt đối không được save khi validate fail.
        verify(courseRepository, never()).save(any(Course.class));
        // Rollback: mock-only, không thay đổi DB.
    }

    // ---------------------------------------------------------------
    // TC-COURSE-003: Thiếu categoryId
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra createCourse từ chối request thiếu categoryId.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   IS_NULL/CATEGORY và dừng trước bước save.
     * - Kỹ thuật test: dựng request tối thiểu, dùng `assertThrows` cho nhánh lỗi
     *   validate sớm, sau đó verify không có interaction ghi với CourseRepository.
     */
    @Test
    @DisplayName("TC-COURSE-003: should_ThrowException_When_CategoryIdIsMissing")
    void should_ThrowException_When_CategoryIdIsMissing() {
        CourseRequest request = new CourseRequest();
        request.setTitle("X");
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(null);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.CATEGORY, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-004: Thiếu authorId
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: xác nhận createCourse không cho tạo khóa học khi thiếu authorId.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   IS_NULL/USER, vì không xác định được tác giả khóa học.
     * - Kỹ thuật test: kiểm thử negative path bằng `assertThrows` và kiểm tra
     *   `courseRepository.save` không bao giờ được gọi.
     */
    @Test
    @DisplayName("TC-COURSE-004: should_ThrowException_When_AuthorIdIsMissing")
    void should_ThrowException_When_AuthorIdIsMissing() {
        CourseRequest request = new CourseRequest();
        request.setTitle("X");
        request.setPrice(100_000L);
        request.setAuthorId(null);
        request.setCategoryId(1);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-005: Giá <= 0
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra validate giá khóa học, không cho price bằng 0 hoặc âm.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_AVAILABLE/PRICE và không tạo course mới.
     * - Kỹ thuật test: dùng dữ liệu biên `price = 0L`, bắt exception bằng
     *   `assertThrows`, rồi verify repository không save.
     */
    @Test
    @DisplayName("TC-COURSE-005: should_ThrowException_When_PriceIsZeroOrNegative")
    void should_ThrowException_When_PriceIsZeroOrNegative() {
        CourseRequest request = new CourseRequest();
        request.setTitle("X");
        request.setPrice(0L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_AVAILABLE, ex.getResponseCode());
        assertEquals(ResponseObject.PRICE, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-006: Author không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: đảm bảo createCourse kiểm tra authorId có tồn tại trong hệ thống.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/USER khi userRepository.findById trả Optional.empty().
     * - Kỹ thuật test: mock repository trả empty để đi vào nhánh not found,
     *   kết hợp verify không gọi save để tránh tạo dữ liệu mồ côi.
     */
    @Test
    @DisplayName("TC-COURSE-006: should_ThrowException_When_AuthorNotFound")
    void should_ThrowException_When_AuthorNotFound() {
        CourseRequest request = new CourseRequest();
        request.setTitle("X");
        request.setPrice(100_000L);
        request.setAuthorId(999L);
        request.setCategoryId(1);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-030: Cập nhật khóa học hợp lệ
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra updateCourse cập nhật được các trường chính của khóa học
     *   khi course, author và user hiện tại đều hợp lệ.
     * - Kết quả/trả về: test là `void`; service phải trả CourseResponse đã convert,
     *   đồng thời Course lưu xuống có title, description, price, thumbnail, category
     *   và updatedBy đúng theo request/người gọi.
     * - Kỹ thuật test: mock luồng đọc user/course/author, dùng `thenAnswer` giữ nguyên
     *   entity khi save, dùng `ArgumentCaptor` để assert trạng thái sau update.
     */
    @Test
    @DisplayName("TC-COURSE-030: should_UpdateCourse_When_RequestIsValid")
    void should_UpdateCourse_When_RequestIsValid() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setTitle("Java Advanced");
        request.setDescription("Khóa nâng cao");
        request.setPrice(799_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(2);
        request.setThumbnailUrl("new-thumb.png");

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        CourseResponse expected = mock(CourseResponse.class);
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(expected);

        // Act
        CourseResponse actual = courseService.updateCourse(httpRequest, request);

        // Assert
        assertSame(expected, actual);

        // CheckDB: kiểm tra dữ liệu được cập nhật vào Course đúng đặc tả
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(1)).save(captor.capture());
        Course saved = captor.getValue();
        assertEquals("Java Advanced", saved.getTitle());
        assertEquals("Khóa nâng cao", saved.getDescription());
        assertEquals(799_000L, saved.getPrice());
        assertEquals("new-thumb.png", saved.getThumbnailUrl());
        assertEquals(ECategoryCourse.fromValue(2), saved.getCategoryCourse());
        assertEquals(manager, saved.getUpdatedBy(), "updatedBy phải là người gọi (MANAGER)");
    }

    // ---------------------------------------------------------------
    // TC-COURSE-031: Cập nhật khóa học không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: xác nhận updateCourse báo lỗi khi id khóa học không tồn tại.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/COURSE và không ghi dữ liệu.
     * - Kỹ thuật test: mock xác thực user thành công nhưng `courseRepository.findById`
     *   trả Optional.empty(), từ đó kiểm tra nhánh lỗi sau bước tìm course.
     */
    @Test
    @DisplayName("TC-COURSE-031: should_ThrowException_When_UpdateCourseNotFound")
    void should_ThrowException_When_UpdateCourseNotFound() {
        CourseRequest request = new CourseRequest();
        request.setId(999L);
        request.setTitle("X");
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.updateCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.COURSE, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-012: MANAGER xóa mềm khóa học
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra MANAGER có quyền gọi disableOrDeleteCourse để xóa mềm course.
     * - Kết quả/trả về: test là `void`; service trả CourseResponse sau convert, còn
     *   entity được save phải có `isDelete = true`.
     * - Kỹ thuật test: mock phân quyền qua JWT/email, dùng `ArgumentCaptor` để bắt entity
     *   truyền vào save và assert flag xóa mềm.
     */
    @Test
    @DisplayName("TC-COURSE-012: should_SoftDeleteCourse_When_UserIsManager")
    void should_SoftDeleteCourse_When_UserIsManager() {
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setIsDelete(true);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(mock(CourseResponse.class));

        courseService.disableOrDeleteCourse(httpRequest, request);

        // CheckDB: course.isDelete=true sau khi save
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().getIsDelete(),
                "Khóa học bị xóa mềm => isDelete=true. Không được hiển thị ở danh sách công khai.");
    }

    // ---------------------------------------------------------------
    // TC-COURSE-013: CONSULTANT KHÔNG phải tác giả thì KHÔNG có quyền xóa.
    //   (Test này đúng spec: chỉ tác giả hoặc MANAGER mới được xóa.)
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: bảo vệ rule phân quyền, CONSULTANT không sở hữu course thì không
     *   được xóa mềm khóa học của người khác.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_PERMISSION/USER, không trả CourseResponse.
     * - Kỹ thuật test: tạo bối cảnh user hiện tại khác owner, dùng `assertThrows`
     *   cho authorization failure và verify repository không save.
     */
    @Test
    @DisplayName("TC-COURSE-013: should_ThrowException_When_NonOwnerConsultantSoftDeletes")
    void should_ThrowException_When_NonOwnerConsultantSoftDeletes() {
        // Arrange: consultant này KHÔNG phải author của existingCourse.
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setIsDelete(true);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.disableOrDeleteCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB: tuyệt đối không gọi save
        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-014 (BUG-FINDER theo KH397/FLOW_KH_04):
    //   Spec: CONSULTANT là TÁC GIẢ phải xóa mềm/khôi phục được khóa học của mình.
    //   Code thực tế: disableOrDeleteCourse chỉ branch nếu role==MANAGER, các role khác throw.
    //   System test FLOW_KH_04: FAILED.
    //   ⇒ Test này theo spec, đang phơi bày bug.
    // ---------------------------------------------------------------
    /**
     * BUG-FINDER theo KH397/FLOW_KH_04 — để FAIL có chủ đích.
     * Spec: CONSULTANT là tác giả phải xóa được khóa học của mình.
     * Code: disableOrDeleteCourse chỉ branch khi role==MANAGER → throw NOT_PERMISSION ⇒ test FAIL.
     */
    /*
     * Note:
     * - Tác dụng: mô tả kỳ vọng nghiệp vụ rằng CONSULTANT là tác giả/owner được phép
     *   xóa mềm chính course của mình.
     * - Kết quả/trả về: test là `void`; theo spec service không được throw và phải
     *   save course với `isDelete = true`, sau đó trả DTO nếu convert thành công.
     * - Kỹ thuật test: dùng `assertDoesNotThrow` để biến bug phân quyền thành failure
     *   rõ ràng, kết hợp `ArgumentCaptor` kiểm tra flag lưu xuống repository.
     */
    @Test
    @DisplayName("TC-COURSE-014: should_AllowConsultantOwner_To_SoftDeleteOwnCourse")
    void should_AllowConsultantOwner_To_SoftDeleteOwnCourse() {
        // Arrange: existingCourse có createdBy = manager (ban đầu); với spec, consultant là tác giả thì cho xóa.
        Course consultantOwnedCourse = Course.builder()
                .id(200L).title("Course of consultant")
                .price(100_000L).author(consultant).createdBy(consultant)
                .isActive(true).isDelete(false)
                .categoryCourse(existingCourse.getCategoryCourse())
                .build();

        CourseRequest request = new CourseRequest();
        request.setId(consultantOwnedCourse.getId());
        request.setIsDelete(true);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(courseRepository.findById(consultantOwnedCourse.getId())).thenReturn(Optional.of(consultantOwnedCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(mock(CourseResponse.class));

        // Act: theo spec, không được throw; bọc assertDoesNotThrow để báo Failure rõ ràng.
        assertDoesNotThrow(() -> courseService.disableOrDeleteCourse(httpRequest, request),
                "Spec KH397/FLOW_KH_04: CONSULTANT là tác giả phải xóa được course của mình. "
                        + "Code hiện đang throw NOT_PERMISSION cho mọi role khác MANAGER.");

        // CheckDB: phải save với isDelete=true
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().getIsDelete(),
                "CONSULTANT-author phải xóa mềm được course của mình theo FLOW_KH_04");
    }

    // ---------------------------------------------------------------
    // TC-COURSE-022: Lấy chi tiết khóa học không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getCourseDetail báo lỗi khi không tìm thấy course theo id.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/COURSE và không gọi convert DTO.
     * - Kỹ thuật test: mock `findById` trả Optional.empty(), dùng `assertThrows`
     *   và verify interaction với convertUtil là never.
     */
    @Test
    @DisplayName("TC-COURSE-022: should_ThrowException_When_GetCourseDetailNotFound")
    void should_ThrowException_When_GetCourseDetailNotFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.getCourseDetail(httpRequest, 999L));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.COURSE, ex.getResponseObject());

        verify(convertUtil, never()).convertCourseToDto(any(), any());
    }

    // ---------------------------------------------------------------
    // TC-COURSE-033: getCourseDetail thành công (cover return convert)
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getCourseDetail trả thông tin chi tiết khi course tồn tại.
     * - Kết quả/trả về: test là `void`; service phải trả đúng CourseResponse do
     *   convertUtil.convertCourseToDto sinh ra từ existingCourse.
     * - Kỹ thuật test: mock repository trả course, mock converter trả DTO mong đợi,
     *   dùng `assertSame` để kiểm tra đúng reference và verify converter được gọi một lần.
     */
    @Test
    @DisplayName("TC-COURSE-033: should_ReturnCourseDetail_When_CourseExists")
    void should_ReturnCourseDetail_When_CourseExists() {
        // Arrange
        CourseResponse expected = new CourseResponse();
        expected.setId(existingCourse.getId());
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));
        when(convertUtil.convertCourseToDto(httpRequest, existingCourse)).thenReturn(expected);

        // Act
        CourseResponse actual = courseService.getCourseDetail(httpRequest, existingCourse.getId());

        // Assert
        assertSame(expected, actual);
        verify(convertUtil, times(1)).convertCourseToDto(httpRequest, existingCourse);
    }

    // =====================================================================
    //  KH402 — Validate Tiêu đề không hợp lệ (BUG-FINDER)
    //  Spec: rỗng / "  " / chuỗi >255 ký tự / chuỗi <5 ký tự / trùng → đều phải lỗi
    //  Code: chỉ check null hoặc isEmpty()
    // =====================================================================
    /** BUG-FINDER KH402: code chỉ check `null || isEmpty()`, KHÔNG reject `"   "` ⇒ test FAIL. */
    /*
     * Note:
     * - Tác dụng: kiểm tra title chỉ gồm khoảng trắng phải bị xem là không hợp lệ.
     * - Kết quả/trả về: test là `void`; service theo spec phải ném WebToeicException
     *   IS_NULL/TITLE và không tạo CourseResponse.
     * - Kỹ thuật test: dùng dữ liệu bug-finder `"   "`, `assertThrows` để bắt lỗi
     *   validate và `verify(..., never())` để bảo đảm không save.
     */
    @Test
    @DisplayName("TC-COURSE-029: should_ThrowException_When_TitleIsBlankOnly")
    void should_ThrowException_When_TitleIsBlankOnly() {
        CourseRequest request = new CourseRequest();
        request.setTitle("   ");
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.TITLE, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    /** BUG-FINDER KH402: code KHÔNG validate max length 255 ⇒ test FAIL. */
    /*
     * Note:
     * - Tác dụng: kiểm tra giới hạn độ dài tối đa của title là 255 ký tự.
     * - Kết quả/trả về: test là `void`; service theo spec phải ném WebToeicException
     *   INVALID/TITLE khi title dài 256 ký tự.
     * - Kỹ thuật test: tạo chuỗi bằng `"a".repeat(256)` để chạm biên max length,
     *   dùng `assertThrows` và verify không ghi repository.
     */
    @Test
    @DisplayName("TC-COURSE-007: should_ThrowException_When_TitleExceeds255Characters")
    void should_ThrowException_When_TitleExceeds255Characters() {
        CourseRequest request = new CourseRequest();
        request.setTitle("a".repeat(256));
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.INVALID, ex.getResponseCode());
        assertEquals(ResponseObject.TITLE, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    /** BUG-FINDER KH402: code KHÔNG validate min length 5 ⇒ test FAIL. */
    /*
     * Note:
     * - Tác dụng: kiểm tra title quá ngắn phải bị từ chối theo rule min length.
     * - Kết quả/trả về: test là `void`; service theo spec phải ném WebToeicException
     *   INVALID/TITLE, không trả DTO.
     * - Kỹ thuật test: dùng giá trị biên thấp `"Ja"` và `assertThrows` để kiểm tra
     *   nhánh validation thiếu độ dài tối thiểu.
     */
    @Test
    @DisplayName("TC-COURSE-008: should_ThrowException_When_TitleShorterThanMinLength")
    void should_ThrowException_When_TitleShorterThanMinLength() {
        CourseRequest request = new CourseRequest();
        request.setTitle("Ja"); // 2 ký tự
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.INVALID, ex.getResponseCode());
        assertEquals(ResponseObject.TITLE, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    /** BUG-FINDER KH402: code KHÔNG check trùng title ⇒ test FAIL. */
    /*
     * Note:
     * - Tác dụng: xác nhận nghiệp vụ không cho tạo course có title trùng course đang tồn tại.
     * - Kết quả/trả về: test là `void`; service theo spec phải ném WebToeicException
     *   EXISTED/TITLE và dừng trước thao tác save.
     * - Kỹ thuật test: dùng title trùng với fixture `existingCourse`, test đóng vai
     *   bug-finder cho bước kiểm tra unique title trong service/repository.
     */
    @Test
    @DisplayName("TC-COURSE-009: should_ThrowException_When_TitleDuplicated")
    void should_ThrowException_When_TitleDuplicated() {
        CourseRequest request = new CourseRequest();
        request.setTitle("Java Basic"); // trùng với existingCourse
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        // Giả sử sau khi fix, repo có method existsByTitleIgnoreCaseAndIsDeleteFalse
        // Sau fix, service phải gọi method này và throw EXISTED/TITLE.

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.TITLE, ex.getResponseObject());

        verify(courseRepository, never()).save(any(Course.class));
    }

    // =====================================================================
    //  KH412 / KH448 — Trim khoảng trắng đầu/cuối khi tạo / cập nhật (BUG-FINDER)
    //  Spec: input "  IELTS Speaking  " phải lưu thành "IELTS Speaking"
    // =====================================================================
    /** BUG-FINDER KH412/KH448: code KHÔNG trim khoảng trắng đầu/cuối ⇒ test FAIL. */
    /*
     * Note:
     * - Tác dụng: kiểm tra createCourse phải chuẩn hóa title và description bằng trim
     *   trước khi lưu xuống repository.
     * - Kết quả/trả về: test là `void`; service trả DTO sau khi tạo, còn entity save
     *   phải có title/description đã bỏ khoảng trắng đầu cuối.
     * - Kỹ thuật test: mock luồng tạo hợp lệ, dùng `ArgumentCaptor<Course>` để đọc lại
     *   entity được save và assert trực tiếp dữ liệu đã normalize.
     */
    @Test
    @DisplayName("TC-COURSE-010: should_TrimTitleAndDescription_When_CreateCourse")
    void should_TrimTitleAndDescription_When_CreateCourse() {
        CourseRequest request = new CourseRequest();
        request.setTitle("  IELTS Speaking  ");
        request.setDescription("  Mô tả chi tiết  ");
        request.setPrice(100_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(userRepository.findUserOnlyStudent()).thenReturn(java.util.Collections.emptyList());
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(mock(CourseResponse.class));

        courseService.createCourse(httpRequest, request);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        Course saved = captor.getValue();
        assertEquals("IELTS Speaking", saved.getTitle(),
                "Spec KH412: title phải được trim trước khi lưu");
        assertEquals("Mô tả chi tiết", saved.getDescription(),
                "Spec KH412: description cũng phải được trim trước khi lưu");
    }

    // =====================================================================
    //  KH50 / KH191 / KH195 — Search trim & không phân biệt hoa thường (BUG-FINDER)
    //  Spec: " Listening " và " IELTS " phải được trim; "ielts" và "IELTS" phải tương đương.
    //  Code: getCourses chỉ set categories=null nếu rỗng; KHÔNG trim/lowercase searchString.
    // =====================================================================
    /** BUG-FINDER KH50/KH191/KH195: getCourses KHÔNG trim/lowercase searchString ⇒ test FAIL. */
    /*
     * Note:
     * - Tác dụng: kiểm tra getCourses chuẩn hóa searchString trước khi query danh sách.
     * - Kết quả/trả về: test là `void`; service trả Page<CourseResponse> từ repository,
     *   đồng thời DTO truyền xuống repository phải có searchString = "ielts".
     * - Kỹ thuật test: dùng `ArgumentCaptor<SearchBaseDto>` để kiểm tra tham số query
     *   sau khi service xử lý trim/lowercase, không cần DB thật.
     */
    @Test
    @DisplayName("TC-COURSE-023: should_TrimAndLowercaseSearch_When_GetCourses")
    void should_TrimAndLowercaseSearch_When_GetCourses() {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setSearchString("  IELTS  ");
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> emptyPage = new PageImpl<>(java.util.Collections.emptyList());

        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(courseRepository.findCourses(any(SearchBaseDto.class), eq(""), eq(pageable))).thenReturn(emptyPage);

        courseService.getCourses(httpRequest, dto, pageable);

        // CheckDB: dto truyền xuống repository phải có searchString sau khi trim+lowercase
        ArgumentCaptor<SearchBaseDto> captor = ArgumentCaptor.forClass(SearchBaseDto.class);
        verify(courseRepository).findCourses(captor.capture(), eq(""), eq(pageable));
        assertEquals("ielts", captor.getValue().getSearchString(),
                "Spec KH50/KH195: searchString phải được trim; KH191: lowercase trước khi query");
    }

    // ---------------------------------------------------------------
    // TC-COURSE-018: findByCourseBought - phân trang + mapping dto
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra findByCourseBought lấy danh sách khóa học đã mua của user
     *   hiện tại và map từng Course sang CourseResponse.
     * - Kết quả/trả về: test là `void`; service phải trả Page<CourseResponse> có
     *   tổng số phần tử và content đúng theo Page<Course> giả lập.
     * - Kỹ thuật test: mock JWT/user, mock EnrollmentRepository trả `PageImpl`,
     *   mock converter từng phần tử và assert metadata/content của Page.
     */
    @Test
    @DisplayName("TC-COURSE-018: should_ReturnBoughtCoursesPage_When_UserExists")
    void should_ReturnBoughtCoursesPage_When_UserExists() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Course c1 = Course.builder().id(1L).title("Course 1").build();
        Course c2 = Course.builder().id(2L).title("Course 2").build();
        Page<Course> coursePage = new PageImpl<>(List.of(c1, c2), pageable, 2);

        CourseResponse r1 = new CourseResponse();
        r1.setId(1L);
        CourseResponse r2 = new CourseResponse();
        r2.setId(2L);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(enrollmentRepository.findCourseByUser(manager, pageable)).thenReturn(coursePage);
        when(convertUtil.convertCourseToDto(httpRequest, c1)).thenReturn(r1);
        when(convertUtil.convertCourseToDto(httpRequest, c2)).thenReturn(r2);

        // Act
        Page<CourseResponse> result = courseService.findByCourseBought(httpRequest, pageable);

        // Assert
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getId());
        assertEquals(2L, result.getContent().get(1).getId());

        // CheckDB
        verify(enrollmentRepository, times(1)).findCourseByUser(manager, pageable);
        verify(convertUtil, times(1)).convertCourseToDto(httpRequest, c1);
        verify(convertUtil, times(1)).convertCourseToDto(httpRequest, c2);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-019: getAllCourses - categories rỗng phải set null
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getAllCourses normalize categories rỗng thành null trước query.
     * - Kết quả/trả về: test là `void`; service phải trả đúng Page<CourseResponse>
     *   repository trả về và làm `dto.categories` thành null.
     * - Kỹ thuật test: dùng DTO mutable để assert side effect normalize, mock repository
     *   trả Page rỗng và verify gọi đúng `findAllCourses`.
     */
    @Test
    @DisplayName("TC-COURSE-019: should_SetCategoriesNullAndCallFindAllCourses_When_CategoriesIsEmpty")
    void should_SetCategoriesNullAndCallFindAllCourses_When_CategoriesIsEmpty() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(Collections.emptyList());
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(courseRepository.findAllCourses(dto, pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getAllCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertNull(dto.getCategories(), "categories rỗng phải được normalize về null trước khi query");

        // CheckDB
        verify(courseRepository, times(1)).findAllCourses(dto, pageable);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-026: getOwnCourses - lọc theo email hiện tại + categories
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getOwnCourses gắn email người dùng hiện tại vào DTO và
     *   normalize categories rỗng trước khi query course sở hữu.
     * - Kết quả/trả về: test là `void`; service phải trả Page<CourseResponse> từ
     *   repository, DTO sau xử lý có email consultant và categories null.
     * - Kỹ thuật test: mock JWT lấy email, assert side effect trên DTO, verify repository
     *   nhận đúng dto, email và pageable.
     */
    @Test
    @DisplayName("TC-COURSE-026: should_SetEmailAndCallFindOwnCourses_When_GetOwnCourses")
    void should_SetEmailAndCallFindOwnCourses_When_GetOwnCourses() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(Collections.emptyList());
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultant.getEmail());
        when(courseRepository.findOwnCourses(dto, consultant.getEmail(), pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getOwnCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertEquals(consultant.getEmail(), dto.getEmail(),
                "dto.email phải được gắn bằng email user hiện tại trước khi query");
        assertNull(dto.getCategories(), "categories rỗng phải được normalize về null");

        // CheckDB
        verify(courseRepository, times(1)).findOwnCourses(dto, consultant.getEmail(), pageable);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-024: getCourses khi không có/sai Bearer token
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getCourses xử lý request không có Bearer token hợp lệ như
     *   người dùng ẩn danh.
     * - Kết quả/trả về: test là `void`; service phải trả Page<CourseResponse> từ
     *   repository và truyền email rỗng `""` xuống query.
     * - Kỹ thuật test: mock Authorization header sai prefix, verify `jwtUtil` không
     *   được gọi và repository được gọi với email rỗng.
     */
    @Test
    @DisplayName("TC-COURSE-024: should_QueryCoursesWithEmptyEmail_When_MissingOrInvalidBearer")
    void should_QueryCoursesWithEmptyEmail_When_MissingOrInvalidBearer() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(httpRequest.getHeader("Authorization")).thenReturn("Token abc");
        when(courseRepository.findCourses(dto, "", pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        verify(jwtUtil, never()).getEmailFromToken(httpRequest);
        verify(courseRepository, times(1)).findCourses(dto, "", pageable);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-034: getCourses với Bearer hợp lệ lấy email từ token
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getCourses nhận Bearer token hợp lệ thì lấy email user từ JWT
     *   để query trạng thái liên quan đến người dùng đó.
     * - Kết quả/trả về: test là `void`; service phải trả đúng Page<CourseResponse>
     *   và gọi repository với email lấy từ token.
     * - Kỹ thuật test: mock header `Bearer valid-token`, mock `jwtUtil.getEmailFromToken`,
     *   dùng verify để kiểm tra cả bước đọc token và query repository.
     */
    @Test
    @DisplayName("TC-COURSE-034: should_QueryCoursesWithTokenEmail_When_BearerTokenIsValid")
    void should_QueryCoursesWithTokenEmail_When_BearerTokenIsValid() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultant.getEmail());
        when(courseRepository.findCourses(dto, consultant.getEmail(), pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        verify(jwtUtil, times(1)).getEmailFromToken(httpRequest);
        verify(courseRepository, times(1)).findCourses(dto, consultant.getEmail(), pageable);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-025: getCourses giữ nguyên categories khi có dữ liệu
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getCourses không làm mất bộ lọc categories khi danh sách
     *   categories có dữ liệu.
     * - Kết quả/trả về: test là `void`; service trả Page<CourseResponse> từ repository,
     *   còn `dto.categories` vẫn giữ nguyên List.of("1", "2").
     * - Kỹ thuật test: dùng DTO mutable làm đối tượng quan sát side effect, mock request
     *   không có token để tập trung vào logic categories.
     */
    @Test
    @DisplayName("TC-COURSE-025: should_KeepCategories_When_GetCoursesWithNonEmptyCategories")
    void should_KeepCategories_When_GetCoursesWithNonEmptyCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(List.of("1", "2"));
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(courseRepository.findCourses(dto, "", pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertEquals(List.of("1", "2"), dto.getCategories(),
                "categories có dữ liệu thì không được reset về null");
    }

    // ---------------------------------------------------------------
    // TC-COURSE-020: getAllCourses với categories = null
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getAllCourses giữ nguyên categories = null khi request
     *   không truyền bộ lọc category.
     * - Kết quả/trả về: test là `void`; service phải trả Page<CourseResponse> từ
     *   repository và không tự tạo danh sách categories mới.
     * - Kỹ thuật test: mock repository trả `PageImpl` rỗng, dùng assertNull để kiểm tra
     *   DTO sau xử lý và verify query đúng một lần.
     */
    @Test
    @DisplayName("TC-COURSE-020: should_KeepCategoriesNull_When_GetAllCoursesWithNullCategories")
    void should_KeepCategoriesNull_When_GetAllCoursesWithNullCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(courseRepository.findAllCourses(dto, pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getAllCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertNull(dto.getCategories());
        verify(courseRepository, times(1)).findAllCourses(dto, pageable);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-021: getAllCourses giữ nguyên categories khi có dữ liệu
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getAllCourses bảo toàn filter categories đã được người dùng chọn.
     * - Kết quả/trả về: test là `void`; service trả Page<CourseResponse> như repository
     *   và `dto.categories` vẫn là List.of("1").
     * - Kỹ thuật test: test side effect trên DTO sau khi gọi service, mock repository
     *   để tách khỏi tầng DB.
     */
    @Test
    @DisplayName("TC-COURSE-021: should_KeepCategories_When_GetAllCoursesWithNonEmptyCategories")
    void should_KeepCategories_When_GetAllCoursesWithNonEmptyCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(List.of("1"));
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(courseRepository.findAllCourses(dto, pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getAllCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertEquals(List.of("1"), dto.getCategories());
    }

    // ---------------------------------------------------------------
    // TC-COURSE-027: getOwnCourses với categories = null
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getOwnCourses khi categories null vẫn gắn email người dùng
     *   hiện tại và không thay đổi filter category.
     * - Kết quả/trả về: test là `void`; service trả Page<CourseResponse> từ repository,
     *   DTO có email consultant và categories vẫn null.
     * - Kỹ thuật test: mock JWT để xác định user hiện tại, assert trạng thái DTO và
     *   verify repository `findOwnCourses` nhận đúng pageable.
     */
    @Test
    @DisplayName("TC-COURSE-027: should_KeepCategoriesNullAndSetEmail_When_GetOwnCoursesWithNullCategories")
    void should_KeepCategoriesNullAndSetEmail_When_GetOwnCoursesWithNullCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultant.getEmail());
        when(courseRepository.findOwnCourses(dto, consultant.getEmail(), pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getOwnCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertEquals(consultant.getEmail(), dto.getEmail());
        assertNull(dto.getCategories());
        verify(courseRepository, times(1)).findOwnCourses(dto, consultant.getEmail(), pageable);
    }

    // ---------------------------------------------------------------
    // TC-COURSE-028: getOwnCourses giữ nguyên categories khi có dữ liệu
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra getOwnCourses bảo toàn categories có dữ liệu trong khi vẫn
     *   lọc theo email của consultant hiện tại.
     * - Kết quả/trả về: test là `void`; service trả đúng Page<CourseResponse>, DTO có
     *   email consultant và categories = List.of("2").
     * - Kỹ thuật test: mock JWT + repository, sau đó assert trực tiếp side effect trên
     *   SearchBaseDto thay vì kiểm tra SQL/DB thật.
     */
    @Test
    @DisplayName("TC-COURSE-028: should_KeepCategories_When_GetOwnCoursesWithNonEmptyCategories")
    void should_KeepCategories_When_GetOwnCoursesWithNonEmptyCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(List.of("2"));
        Pageable pageable = PageRequest.of(0, 10);
        Page<CourseResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultant.getEmail());
        when(courseRepository.findOwnCourses(dto, consultant.getEmail(), pageable)).thenReturn(expectedPage);

        // Act
        Page<CourseResponse> result = courseService.getOwnCourses(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertEquals(consultant.getEmail(), dto.getEmail());
        assertEquals(List.of("2"), dto.getCategories());
    }

    // ---------------------------------------------------------------
    // TC-COURSE-011: createCourse khi user tạo (createdBy) không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra createCourse báo lỗi khi email trong token không map được
     *   tới user tạo khóa học.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/USER và không save course.
     * - Kỹ thuật test: mock author tồn tại nhưng current user không tồn tại, dùng
     *   `assertThrows` để cô lập nhánh lỗi createdBy.
     */
    @Test
    @DisplayName("TC-COURSE-011: should_ThrowException_When_CreatedByUserNotFound")
    void should_ThrowException_When_CreatedByUserNotFound() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setTitle("Java Basic");
        request.setDescription("Desc");
        request.setPrice(499_000L);
        request.setAuthorId(author.getId());
        request.setCategoryId(1);
        request.setThumbnailUrl("img.png");

        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("notfound@learnez.vn");
        when(userRepository.findByEmail("notfound@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.createCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-032: updateCourse khi authorId không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra updateCourse dừng lại khi authorId mới không tồn tại.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/USER và không lưu thay đổi vào course.
     * - Kỹ thuật test: mock user hiện tại và course tồn tại, riêng author lookup trả
     *   Optional.empty() để kiểm tra đúng nhánh lỗi.
     */
    @Test
    @DisplayName("TC-COURSE-032: should_ThrowException_When_UpdateCourseAuthorNotFound")
    void should_ThrowException_When_UpdateCourseAuthorNotFound() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setTitle("New title");
        request.setAuthorId(999L);
        request.setCategoryId(1);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.updateCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-015: disableOrDelete khi user hiện tại không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra disableOrDeleteCourse yêu cầu user hiện tại phải tồn tại
     *   trước khi đọc hoặc cập nhật course.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/USER, không tìm course và không save.
     * - Kỹ thuật test: mock JWT trả email lạ, `userRepository.findByEmail` trả empty,
     *   rồi verify cả `findById` và `save` đều không được gọi.
     */
    @Test
    @DisplayName("TC-COURSE-015: should_ThrowException_When_DisableOrDeleteUserNotFound")
    void should_ThrowException_When_DisableOrDeleteUserNotFound() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setIsDelete(true);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("unknown@learnez.vn");
        when(userRepository.findByEmail("unknown@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.disableOrDeleteCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(courseRepository, never()).findById(any());
        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-016: disableOrDelete khi course không tồn tại
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra disableOrDeleteCourse báo lỗi khi request trỏ tới course
     *   không tồn tại.
     * - Kết quả/trả về: test là `void`; service phải ném WebToeicException
     *   NOT_EXISTED/COURSE và không save.
     * - Kỹ thuật test: mock xác thực MANAGER thành công, mock `findById` trả empty
     *   để tập trung vào nhánh course not found.
     */
    @Test
    @DisplayName("TC-COURSE-016: should_ThrowException_When_DisableOrDeleteCourseNotFound")
    void should_ThrowException_When_DisableOrDeleteCourseNotFound() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setId(999L);
        request.setIsDelete(true);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> courseService.disableOrDeleteCourse(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.COURSE, ex.getResponseObject());

        // CheckDB
        verify(courseRepository, never()).save(any(Course.class));
    }

    // ---------------------------------------------------------------
    // TC-COURSE-035: disableOrDelete cập nhật isActive (line 160-161)
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra disableOrDeleteCourse cập nhật trạng thái active khi
     *   request.isActive khác trạng thái hiện tại của course.
     * - Kết quả/trả về: test là `void`; service trả DTO sau save, còn entity lưu xuống
     *   phải có `isActive = false`.
     * - Kỹ thuật test: dùng fixture đang active=true, gửi request active=false,
     *   bắt entity bằng `ArgumentCaptor` để assert flag sau xử lý.
     */
    @Test
    @DisplayName("TC-COURSE-035: should_UpdateIsActive_When_DisableOrDeleteWithDifferentIsActive")
    void should_UpdateIsActive_When_DisableOrDeleteWithDifferentIsActive() {
        // Arrange
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setIsActive(false); // existing đang true => phải set về false

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(new CourseResponse());

        // Act
        courseService.disableOrDeleteCourse(httpRequest, request);

        // Assert / CheckDB
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(1)).save(captor.capture());
        assertFalse(captor.getValue().getIsActive(),
                "Khi request.isActive khác giá trị hiện tại, service phải cập nhật isActive theo request.");
    }

    // ---------------------------------------------------------------
    // TC-COURSE-017: disableOrDelete không đổi isActive/isDelete khi cùng giá trị
    // ---------------------------------------------------------------
    /*
     * Note:
     * - Tác dụng: kiểm tra disableOrDeleteCourse giữ nguyên flags khi request gửi
     *   cùng giá trị với course hiện tại.
     * - Kết quả/trả về: test là `void`; service vẫn save/convert theo luồng hiện tại,
     *   nhưng entity sau xử lý phải giữ `isActive = true` và `isDelete = false`.
     * - Kỹ thuật test: mock save trả lại entity, dùng `ArgumentCaptor` để xác nhận
     *   service không đảo flag ngoài ý muốn.
     */
    @Test
    @DisplayName("TC-COURSE-017: should_KeepFlags_When_DisableOrDeleteWithSameValues")
    void should_KeepFlags_When_DisableOrDeleteWithSameValues() {
        // Arrange: existingCourse đang isActive=true, isDelete=false
        CourseRequest request = new CourseRequest();
        request.setId(existingCourse.getId());
        request.setIsActive(true);
        request.setIsDelete(false);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(existingCourse.getId())).thenReturn(Optional.of(existingCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        when(convertUtil.convertCourseToDto(eq(httpRequest), any(Course.class))).thenReturn(new CourseResponse());

        // Act
        courseService.disableOrDeleteCourse(httpRequest, request);

        // Assert
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().getIsActive());
        assertFalse(captor.getValue().getIsDelete());
    }
}
