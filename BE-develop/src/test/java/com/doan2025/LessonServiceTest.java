package com.doan2025;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.AttachDocumentLesson;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.Lesson;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.LessonRequest;
import com.doan2025.webtoeic.dto.response.LessonResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.AttachDocumentLessonRepository;
import com.doan2025.webtoeic.repository.CourseRepository;
import com.doan2025.webtoeic.repository.LessonRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.CloudService;
import com.doan2025.webtoeic.service.impl.LessonServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bộ unit test cho {@link LessonServiceImpl} thuộc module Khóa học online.
 *
 * <p>Bám đặc tả nghiệp vụ "Quản lý bài học":
 * tạo / cập nhật / xóa mềm bài học, validate trường bắt buộc, kiểm tra phân quyền
 * MANAGER và CONSULTANT (chỉ người tạo mới được sửa) và xử lý tài liệu đính kèm.</p>
 *
 * <p><b>Mocking external dependencies:</b> {@link com.doan2025.webtoeic.service.CloudService}
 * được mock để KHÔNG upload file thật. Mọi link tài liệu/video chỉ là string giả lập.</p>
 *
 * <p><b>Rollback:</b> unit test thuần Mockito, không có DB thật, không cần rollback vật lý.
 * Mọi tương tác repository được kiểm tra qua verify().</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private AttachDocumentLessonRepository attachDocumentLessonRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ModelMapper modelMapper;
    @Mock private CloudService cloudService;     // Mock để không upload/chuyển file thật.
    @Mock private ConvertUtil convertUtil;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private LessonServiceImpl lessonService;

    private User manager;
    private User consultantOwner;     // Consultant đồng thời là người tạo lesson => được sửa
    private User consultantStranger;  // Consultant khác, không phải người tạo => không được sửa
    private Course course;
    private Lesson existingLesson;

    @BeforeEach
    void setUp() {
        manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@learnez.vn");
        manager.setRole(ERole.MANAGER);

        consultantOwner = new User();
        consultantOwner.setId(2L);
        consultantOwner.setEmail("owner@learnez.vn");
        consultantOwner.setRole(ERole.CONSULTANT);

        consultantStranger = new User();
        consultantStranger.setId(3L);
        consultantStranger.setEmail("stranger@learnez.vn");
        consultantStranger.setRole(ERole.CONSULTANT);

        course = Course.builder()
                .id(100L)
                .title("Java Basic")
                .lessons(new ArrayList<>(Arrays.asList(
                        Lesson.builder().id(11L).orderIndex(1).build(),
                        Lesson.builder().id(12L).orderIndex(2).build()
                )))
                .build();

        existingLesson = Lesson.builder()
                .id(50L)
                .title("Lesson 1")
                .content("Content 1")
                .videoUrl("v1.mp4")
                .duration(120.0)
                .orderIndex(1)
                .isPreviewAble(false)
                .isActive(true)
                .isDelete(false)
                .course(course)
                .createdBy(consultantOwner)
                .build();
    }

    // ---------------------------------------------------------------
    // TC-LESSON-001: Tạo bài học hợp lệ + 2 tài liệu đính kèm.
    //   Lưu ý: KHÔNG còn assert orderIndex auto-assign (đã chuyển sang TC-LESSON-006 — bug-finder)
    //   Test này tập trung vào: title/content/videoUrl/isPreviewAble/createdBy/2 tài liệu.
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-001: should_CreateLesson_When_RequestIsValidAndAttachTwoDocuments")
    void should_CreateLesson_When_RequestIsValidAndAttachTwoDocuments() {
        // Arrange
        LessonRequest request = LessonRequest.builder()
                .courseId(course.getId())
                .title("L1")
                .content("abc")
                .videoUrl("v.mp4")
                .duration(120.0)
                .orderIndex(3)
                .isPreviewAble(true)
                .documentUrls(Arrays.asList("d1.pdf", "d2.pdf"))
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(userRepository.findByEmail(consultantOwner.getEmail())).thenReturn(Optional.of(consultantOwner));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> {
            Lesson l = inv.getArgument(0);
            l.setId(999L);
            return l;
        });
        when(attachDocumentLessonRepository.findAllByLessonId(999L)).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(eq(httpRequest), any(Lesson.class), anyList()))
                .thenReturn(mock(LessonResponse.class));

        // Act
        LessonResponse actual = lessonService.createLesson(httpRequest, request);

        // Assert
        assertNotNull(actual);

        // CheckDB: kiểm tra Lesson được lưu đúng đặc tả (trừ orderIndex - xem TC-LESSON-006)
        ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository, times(1)).save(lessonCaptor.capture());
        Lesson saved = lessonCaptor.getValue();
        assertEquals("L1", saved.getTitle());
        assertEquals("abc", saved.getContent());
        assertEquals("v.mp4", saved.getVideoUrl());
        assertTrue(saved.getIsPreviewAble(),
                "Học viên có thể preview lesson này theo đặc tả vì isPreviewAble=true");
        assertEquals(consultantOwner, saved.getCreatedBy());

        // CheckDB: 2 tài liệu đính kèm phải được lưu đúng linkUrl
        ArgumentCaptor<AttachDocumentLesson> docCaptor = ArgumentCaptor.forClass(AttachDocumentLesson.class);
        verify(attachDocumentLessonRepository, times(2)).save(docCaptor.capture());
        List<String> links = docCaptor.getAllValues().stream().map(AttachDocumentLesson::getLinkUrl).toList();
        assertTrue(links.containsAll(Arrays.asList("d1.pdf", "d2.pdf")));

        // Rollback: mock-only, không thay đổi DB.
    }

    // ---------------------------------------------------------------
    // TC-LESSON-006 (BUG-FINDER theo KH425):
    //   Spec: Validate Thứ tự hợp lệ — user nhập số nguyên dương chưa tồn tại
    //         → hệ thống chấp nhận giá trị do user nhập.
    //   Code thực tế: createLesson auto-set orderIndex = course.getLessons().size() + 1,
    //         BỎ QUA giá trị từ LessonRequest#orderIndex.
    //   System test KH425: FAILED.
    // ---------------------------------------------------------------
    /**
     * BUG-FINDER KH425 — để FAIL có chủ đích.
     * Spec: hệ thống chấp nhận orderIndex user nhập (số nguyên dương chưa tồn tại).
     * Code: createLesson ép `course.getLessons().size() + 1`, BỎ QUA `lessonRequest.orderIndex`.
     * ⇒ Test này phải FAIL: assert ra giá trị không khớp.
     */
    @Test
    @DisplayName("TC-LESSON-006: should_UseUserProvidedOrderIndex_When_CreateLesson")
    void should_UseUserProvidedOrderIndex_When_CreateLesson() {
        // Arrange: course có 2 bài, user yêu cầu orderIndex = 5 (chưa tồn tại)
        LessonRequest request = LessonRequest.builder()
                .courseId(course.getId())
                .title("B5")
                .content("nd")
                .videoUrl("v.mp4")
                .orderIndex(5) // user-provided
                .isPreviewAble(false)
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(userRepository.findByEmail(consultantOwner.getEmail())).thenReturn(Optional.of(consultantOwner));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attachDocumentLessonRepository.findAllByLessonId(any())).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(eq(httpRequest), any(Lesson.class), anyList()))
                .thenReturn(mock(LessonResponse.class));

        lessonService.createLesson(httpRequest, request);

        // CheckDB: orderIndex phải = 5 (từ request) chứ không phải 3 (auto-assign)
        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getOrderIndex().intValue(),
                "Spec KH425: orderIndex phải lấy từ LessonRequest.orderIndex");
    }

    // ---------------------------------------------------------------
    // TC-LESSON-002: Thiếu title
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-002: should_ThrowException_When_TitleIsMissing")
    void should_ThrowException_When_TitleIsMissing() {
        LessonRequest request = LessonRequest.builder()
                .courseId(course.getId())
                .title("   ")
                .content("abc")
                .videoUrl("v.mp4")
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(userRepository.findByEmail(consultantOwner.getEmail())).thenReturn(Optional.of(consultantOwner));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.createLesson(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.TITLE, ex.getResponseObject());

        verify(lessonRepository, never()).save(any(Lesson.class));
        verify(attachDocumentLessonRepository, never()).save(any(AttachDocumentLesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-003: Thiếu content vẫn tạo được (theo đặc tả bạn chốt)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-003: should_CreateLesson_When_ContentIsMissing")
    void should_CreateLesson_When_ContentIsMissing() {
        LessonRequest request = LessonRequest.builder()
                .courseId(course.getId())
                .title("L1")
                .content(null)
                .videoUrl("v.mp4")
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(userRepository.findByEmail(consultantOwner.getEmail())).thenReturn(Optional.of(consultantOwner));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> {
            Lesson l = inv.getArgument(0);
            l.setId(3001L);
            return l;
        });
        when(attachDocumentLessonRepository.findAllByLessonId(3001L)).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(eq(httpRequest), any(Lesson.class), anyList()))
                .thenReturn(new LessonResponse());

        // Theo đặc tả bạn chốt: content không bắt buộc.
        // Nếu code hiện tại vẫn đang bắt buộc content, test này sẽ fail để báo mismatch.
        assertDoesNotThrow(() -> lessonService.createLesson(httpRequest, request),
                "Spec đã chốt: thiếu content vẫn phải tạo được lesson.");

        verify(lessonRepository, times(1)).save(any(Lesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-004: Thiếu videoUrl
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-004: should_ThrowException_When_VideoUrlIsMissing")
    void should_ThrowException_When_VideoUrlIsMissing() {
        LessonRequest request = LessonRequest.builder()
                .courseId(course.getId())
                .title("L1")
                .content("abc")
                .videoUrl("")
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(userRepository.findByEmail(consultantOwner.getEmail())).thenReturn(Optional.of(consultantOwner));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.createLesson(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.URL, ex.getResponseObject());

        verify(lessonRepository, never()).save(any(Lesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-005: courseId không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-005: should_ThrowException_When_CourseNotFound")
    void should_ThrowException_When_CourseNotFound() {
        LessonRequest request = LessonRequest.builder()
                .courseId(999L)
                .title("L1")
                .content("abc")
                .videoUrl("v.mp4")
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(userRepository.findByEmail(consultantOwner.getEmail())).thenReturn(Optional.of(consultantOwner));
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.createLesson(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.COURSE, ex.getResponseObject());

        verify(lessonRepository, never()).save(any(Lesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-007: MANAGER cập nhật lesson + thay tài liệu
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-007: should_UpdateLessonAndReplaceDocuments_When_UserIsManager")
    void should_UpdateLessonAndReplaceDocuments_When_UserIsManager() {
        LessonRequest request = LessonRequest.builder()
                .id(existingLesson.getId())
                .title("New title")
                .videoUrl("newv.mp4")
                .documentUrls(Collections.singletonList("new1.pdf"))
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(lessonRepository.findById(existingLesson.getId())).thenReturn(Optional.of(existingLesson));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attachDocumentLessonRepository.findAllByLessonId(existingLesson.getId())).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(eq(httpRequest), any(Lesson.class), anyList()))
                .thenReturn(mock(LessonResponse.class));

        lessonService.updateLesson(httpRequest, request);

        // CheckDB: tài liệu cũ phải bị xóa, tài liệu mới được lưu
        verify(attachDocumentLessonRepository, times(1))
                .deleteAttachDocumentLessonsByLesson_Id(existingLesson.getId());
        ArgumentCaptor<AttachDocumentLesson> docCaptor = ArgumentCaptor.forClass(AttachDocumentLesson.class);
        verify(attachDocumentLessonRepository, times(1)).save(docCaptor.capture());
        assertEquals("new1.pdf", docCaptor.getValue().getLinkUrl());

        // CheckDB: lesson title/videoUrl được cập nhật
        ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository, times(1)).save(lessonCaptor.capture());
        Lesson saved = lessonCaptor.getValue();
        assertEquals("New title", saved.getTitle());
        assertEquals("newv.mp4", saved.getVideoUrl());
    }

    // ---------------------------------------------------------------
    // TC-LESSON-008: CONSULTANT khác người tạo cố cập nhật
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-008: should_ThrowException_When_ConsultantNotOwnerUpdates")
    void should_ThrowException_When_ConsultantNotOwnerUpdates() {
        LessonRequest request = LessonRequest.builder()
                .id(existingLesson.getId())
                .title("Hacked")
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantStranger.getEmail());
        when(userRepository.findByEmail(consultantStranger.getEmail())).thenReturn(Optional.of(consultantStranger));
        when(lessonRepository.findById(existingLesson.getId())).thenReturn(Optional.of(existingLesson));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.updateLesson(httpRequest, request));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());

        verify(lessonRepository, never()).save(any(Lesson.class));
        verify(attachDocumentLessonRepository, never())
                .deleteAttachDocumentLessonsByLesson_Id(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-LESSON-010: MANAGER xóa mềm bài học
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-010: should_SoftDeleteLesson_When_UserIsManager")
    void should_SoftDeleteLesson_When_UserIsManager() {
        LessonRequest request = LessonRequest.builder()
                .id(existingLesson.getId())
                .isDelete(true)
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(lessonRepository.findById(existingLesson.getId())).thenReturn(Optional.of(existingLesson));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attachDocumentLessonRepository.findAllByLessonId(existingLesson.getId())).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(eq(httpRequest), any(Lesson.class), anyList()))
                .thenReturn(mock(LessonResponse.class));

        lessonService.disableOrDelete(httpRequest, request);

        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().getIsDelete(),
                "Bài học bị xóa mềm => isDelete=true. Không hiển thị trong danh sách public.");
    }

    // ---------------------------------------------------------------
    // TC-LESSON-011: CONSULTANT khác người tạo xóa mềm
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-011: should_ThrowException_When_ConsultantNotOwnerSoftDeletes")
    void should_ThrowException_When_ConsultantNotOwnerSoftDeletes() {
        LessonRequest request = LessonRequest.builder()
                .id(existingLesson.getId())
                .isDelete(true)
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantStranger.getEmail());
        when(userRepository.findByEmail(consultantStranger.getEmail())).thenReturn(Optional.of(consultantStranger));
        when(lessonRepository.findById(existingLesson.getId())).thenReturn(Optional.of(existingLesson));

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.disableOrDelete(httpRequest, request));
        assertEquals(ResponseCode.NOT_PERMISSION, ex.getResponseCode());

        verify(lessonRepository, never()).save(any(Lesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-013: getDetail không tồn tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-013: should_ThrowException_When_GetLessonDetailNotFound")
    void should_ThrowException_When_GetLessonDetailNotFound() {
        when(lessonRepository.findById(999L)).thenReturn(Optional.empty());

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.getDetail(httpRequest, 999L));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.LESSON, ex.getResponseObject());

        // Không truy cập attachment khi lesson không tồn tại
        verify(attachDocumentLessonRepository, never()).findAllByLessonId(anyLong());
    }

    // ---------------------------------------------------------------
    // TC-LESSON-014: getDetail thành công
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-014: should_ReturnLessonDetailWithAttachments_When_LessonExists")
    void should_ReturnLessonDetailWithAttachments_When_LessonExists() {
        // Arrange
        AttachDocumentLesson doc = AttachDocumentLesson.builder().id(201L).linkUrl("lesson-doc.pdf").build();
        LessonResponse expected = new LessonResponse();
        expected.setId(existingLesson.getId());

        when(lessonRepository.findById(existingLesson.getId())).thenReturn(Optional.of(existingLesson));
        when(attachDocumentLessonRepository.findAllByLessonId(existingLesson.getId())).thenReturn(List.of(doc));
        when(convertUtil.convertLessonToDto(httpRequest, existingLesson, List.of(doc))).thenReturn(expected);

        // Act
        LessonResponse actual = lessonService.getDetail(httpRequest, existingLesson.getId());

        // Assert
        assertSame(expected, actual);
        verify(lessonRepository, times(1)).findById(existingLesson.getId());
        verify(attachDocumentLessonRepository, times(1)).findAllByLessonId(existingLesson.getId());
    }

    // ---------------------------------------------------------------
    // TC-LESSON-015: getLessons enrich attach document cho từng lesson
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-015: should_EnrichAttachDocuments_When_GetLessons")
    void should_EnrichAttachDocuments_When_GetLessons() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setId(course.getId());
        dto.setCategories(Collections.emptyList());
        Pageable pageable = PageRequest.of(0, 10);

        LessonResponse l1 = new LessonResponse();
        l1.setId(11L);
        l1.setTitle("Lesson 1");
        LessonResponse l2 = new LessonResponse();
        l2.setId(12L);
        l2.setTitle("Lesson 2");

        Page<LessonResponse> lessonPage = new PageImpl<>(new ArrayList<>(List.of(l1, l2)), pageable, 2);

        AttachDocumentLesson doc1 = AttachDocumentLesson.builder().id(101L).linkUrl("d1.pdf").isActive(true).isDelete(false).build();
        AttachDocumentLesson doc2 = AttachDocumentLesson.builder().id(102L).linkUrl("d2.pdf").isActive(true).isDelete(false).build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(lessonRepository.findLessons(dto, consultantOwner.getEmail(), pageable)).thenReturn(lessonPage);
        when(attachDocumentLessonRepository.findAllByLessonId(11L)).thenReturn(List.of(doc1));
        when(attachDocumentLessonRepository.findAllByLessonId(12L)).thenReturn(List.of(doc2));

        // Act
        Page<LessonResponse> result = lessonService.getLessons(httpRequest, dto, pageable);

        // Assert
        assertEquals(2, result.getTotalElements());
        assertNull(dto.getCategories(), "categories rỗng phải normalize về null");
        assertEquals(1, result.getContent().get(0).getAttachDocumentLessons().size());
        assertEquals("d1.pdf", result.getContent().get(0).getAttachDocumentLessons().get(0).getLinkUrl());
        assertEquals(1, result.getContent().get(1).getAttachDocumentLessons().size());
        assertEquals("d2.pdf", result.getContent().get(1).getAttachDocumentLessons().get(0).getLinkUrl());

        // CheckDB
        verify(lessonRepository, times(1)).findLessons(dto, consultantOwner.getEmail(), pageable);
        verify(attachDocumentLessonRepository, times(1)).findAllByLessonId(11L);
        verify(attachDocumentLessonRepository, times(1)).findAllByLessonId(12L);
    }

    // ---------------------------------------------------------------
    // TC-LESSON-016: getLessons không có Bearer token => email rỗng
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-016: should_QueryLessonsWithEmptyEmail_When_NoBearerToken")
    void should_QueryLessonsWithEmptyEmail_When_NoBearerToken() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<LessonResponse> lessonPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(lessonRepository.findLessons(dto, "", pageable)).thenReturn(lessonPage);

        // Act
        Page<LessonResponse> result = lessonService.getLessons(httpRequest, dto, pageable);

        // Assert
        assertEquals(0, result.getTotalElements());
        verify(jwtUtil, never()).getEmailFromToken(httpRequest);
        verify(lessonRepository, times(1)).findLessons(dto, "", pageable);
    }

    // ---------------------------------------------------------------
    // TC-LESSON-017: getOwnLessons lọc theo email hiện tại
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-017: should_CallFindOwnLessonsWithCurrentEmail_When_GetOwnLessons")
    void should_CallFindOwnLessonsWithCurrentEmail_When_GetOwnLessons() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(Collections.emptyList());
        Pageable pageable = PageRequest.of(0, 10);
        Page<LessonResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(lessonRepository.findOwnLessons(dto, consultantOwner.getEmail(), pageable)).thenReturn(expectedPage);

        // Act
        Page<LessonResponse> result = lessonService.getOwnLessons(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertNull(dto.getCategories(), "categories rỗng phải normalize về null");

        // CheckDB
        verify(lessonRepository, times(1)).findOwnLessons(dto, consultantOwner.getEmail(), pageable);
    }

    // ---------------------------------------------------------------
    // TC-LESSON-018: getOwnLessons với categories=null vẫn query được
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-018: should_KeepCategoriesNull_When_GetOwnLessonsWithNullCategories")
    void should_KeepCategoriesNull_When_GetOwnLessonsWithNullCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<LessonResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(consultantOwner.getEmail());
        when(lessonRepository.findOwnLessons(dto, consultantOwner.getEmail(), pageable)).thenReturn(expectedPage);

        // Act
        Page<LessonResponse> result = lessonService.getOwnLessons(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertNull(dto.getCategories());
        verify(lessonRepository, times(1)).findOwnLessons(dto, consultantOwner.getEmail(), pageable);
    }

    // ---------------------------------------------------------------
    // TC-LESSON-019: getAllLessons trả danh sách toàn bộ theo filter
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-019: should_CallFindAllLessons_When_GetAllLessons")
    void should_CallFindAllLessons_When_GetAllLessons() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(Collections.emptyList());
        Pageable pageable = PageRequest.of(0, 10);
        Page<LessonResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(lessonRepository.findAllLessons(dto, pageable)).thenReturn(expectedPage);

        // Act
        Page<LessonResponse> result = lessonService.getAllLessons(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertNull(dto.getCategories(), "categories rỗng phải normalize về null");

        // CheckDB
        verify(lessonRepository, times(1)).findAllLessons(dto, pageable);
    }

    // ---------------------------------------------------------------
    // TC-LESSON-020: getAllLessons với categories=null vẫn query được
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-020: should_KeepCategoriesNull_When_GetAllLessonsWithNullCategories")
    void should_KeepCategoriesNull_When_GetAllLessonsWithNullCategories() {
        // Arrange
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<LessonResponse> expectedPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(lessonRepository.findAllLessons(dto, pageable)).thenReturn(expectedPage);

        // Act
        Page<LessonResponse> result = lessonService.getAllLessons(httpRequest, dto, pageable);

        // Assert
        assertSame(expectedPage, result);
        assertNull(dto.getCategories());
        verify(lessonRepository, times(1)).findAllLessons(dto, pageable);
    }

    // ---------------------------------------------------------------
    // TC-LESSON-009: updateLesson khi không tìm thấy user
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-009: should_ThrowException_When_UpdateLessonUserNotFound")
    void should_ThrowException_When_UpdateLessonUserNotFound() {
        // Arrange
        LessonRequest request = LessonRequest.builder().id(existingLesson.getId()).title("new").build();
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn("unknown@learnez.vn");
        when(userRepository.findByEmail("unknown@learnez.vn")).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.updateLesson(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.USER, ex.getResponseObject());

        // CheckDB
        verify(lessonRepository, never()).findById(anyLong());
        verify(lessonRepository, never()).save(any(Lesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-012: disableOrDelete khi không tìm thấy lesson
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-012: should_ThrowException_When_DisableOrDeleteLessonNotFound")
    void should_ThrowException_When_DisableOrDeleteLessonNotFound() {
        // Arrange
        LessonRequest request = LessonRequest.builder().id(999L).isDelete(true).build();
        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(lessonRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> lessonService.disableOrDelete(httpRequest, request));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.LESSON, ex.getResponseObject());

        // CheckDB
        verify(lessonRepository, never()).save(any(Lesson.class));
    }

    // ---------------------------------------------------------------
    // TC-LESSON-021: disableOrDelete cập nhật isActive (nhánh line 121-122)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TC-LESSON-021: should_UpdateIsActive_When_DisableOrDeleteWithDifferentIsActive")
    void should_UpdateIsActive_When_DisableOrDeleteWithDifferentIsActive() {
        // Arrange
        LessonRequest request = LessonRequest.builder()
                .id(existingLesson.getId())
                .isActive(false) // existing đang true => phải set lại
                .build();

        when(jwtUtil.getEmailFromToken(httpRequest)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(lessonRepository.findById(existingLesson.getId())).thenReturn(Optional.of(existingLesson));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attachDocumentLessonRepository.findAllByLessonId(existingLesson.getId())).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(eq(httpRequest), any(Lesson.class), anyList()))
                .thenReturn(new LessonResponse());

        // Act
        lessonService.disableOrDelete(httpRequest, request);

        // Assert / CheckDB
        ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository, times(1)).save(captor.capture());
        assertFalse(captor.getValue().getIsActive(),
                "Khi request.isActive khác current value, service phải set lại isActive theo request.");
    }

}