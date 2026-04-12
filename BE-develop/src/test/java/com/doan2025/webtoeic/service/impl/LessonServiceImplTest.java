package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.ERole;
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
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonServiceImplTest {

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private CloudService cloudService;
    @Mock
    private AttachDocumentLessonRepository attachDocumentLessonRepository;
    @Mock
    private ConvertUtil convertUtil;

    @InjectMocks
    private LessonServiceImpl service;

    @Mock
    private HttpServletRequest request;

    private User consultant;
    private User manager;

    @BeforeEach
    void setup() {
        consultant = new User();
        consultant.setId(1L);
        consultant.setEmail("consultant@test.com");
        consultant.setRole(ERole.CONSULTANT);

        manager = new User();
        manager.setId(2L);
        manager.setEmail("manager@test.com");
        manager.setRole(ERole.MANAGER);
    }

    @Test
    void createLesson_missingContent_throwException() {
        LessonRequest req = LessonRequest.builder()
                .courseId(9L)
                .title("L1")
                .content(" ")
                .videoUrl("v")
                .build();

        Course course = Course.builder().id(9L).lessons(new ArrayList<>()).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));

        assertThrows(WebToeicException.class, () -> service.createLesson(request, req));
    }

    @Test
    void createLesson_success_saveDocumentsAndReturnDto() {
        LessonRequest req = LessonRequest.builder()
                .courseId(9L)
                .title("L1")
                .content("content")
                .videoUrl("video")
                .isPreviewAble(true)
                .duration(12.0)
                .documentUrls(List.of("doc1", "doc2"))
                .build();

        Course course = Course.builder().id(9L).lessons(new ArrayList<>()).build();
        Lesson savedLesson = Lesson.builder().id(10L).title("L1").build();
        LessonResponse response = new LessonResponse();
        response.setId(10L);
        response.setTitle("L1");

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));
        when(lessonRepository.save(any(Lesson.class))).thenReturn(savedLesson);
        when(attachDocumentLessonRepository.findAllByLessonId(10L)).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(request, savedLesson, Collections.emptyList())).thenReturn(response);

        LessonResponse result = service.createLesson(request, req);

        assertSame(response, result);
        verify(attachDocumentLessonRepository, times(2)).save(any(AttachDocumentLesson.class));
    }

    @Test
    void updateLesson_consultantNotOwner_throwException() {
        LessonRequest req = LessonRequest.builder().id(5L).title("new").build();

        User owner = new User();
        owner.setEmail("other@test.com");

        Lesson lesson = Lesson.builder().id(5L).createdBy(owner).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(lessonRepository.findById(5L)).thenReturn(Optional.of(lesson));

        assertThrows(WebToeicException.class, () -> service.updateLesson(request, req));
    }

    @Test
    void updateLesson_managerWithDocumentUrls_replaceAndSave() {
        LessonRequest req = LessonRequest.builder()
                .id(6L)
                .title("new")
                .content("new-c")
                .videoUrl("new-v")
                .isPreviewAble(true)
                .orderIndex(2)
                .documentUrls(List.of("a", "b"))
                .build();

        Lesson lesson = Lesson.builder().id(6L).createdBy(consultant).build();
        LessonResponse mapped = new LessonResponse();
        mapped.setId(6L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(lessonRepository.findById(6L)).thenReturn(Optional.of(lesson));
        when(lessonRepository.save(lesson)).thenReturn(lesson);
        when(attachDocumentLessonRepository.findAllByLessonId(6L)).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(request, lesson, Collections.emptyList())).thenReturn(mapped);

        LessonResponse result = service.updateLesson(request, req);

        assertSame(mapped, result);
        verify(attachDocumentLessonRepository).deleteAttachDocumentLessonsByLesson_Id(6L);
        verify(attachDocumentLessonRepository, times(2)).save(any(AttachDocumentLesson.class));
    }

    @Test
    void disableOrDelete_consultantOwner_success() {
        LessonRequest req = LessonRequest.builder().id(1L).isActive(false).isDelete(true).build();

        Lesson lesson = Lesson.builder()
                .id(1L)
                .createdBy(consultant)
                .isActive(true)
                .isDelete(false)
                .build();
        LessonResponse mapped = new LessonResponse();
        mapped.setId(1L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
        when(lessonRepository.save(lesson)).thenReturn(lesson);
        when(attachDocumentLessonRepository.findAllByLessonId(1L)).thenReturn(Collections.emptyList());
        when(convertUtil.convertLessonToDto(request, lesson, Collections.emptyList())).thenReturn(mapped);

        LessonResponse result = service.disableOrDelete(request, req);

        assertSame(mapped, result);
        assertFalse(lesson.getIsActive());
        assertTrue(lesson.getIsDelete());
    }

    @Test
    void getLessons_enrichAttachDocuments() {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(Collections.emptyList());

        LessonResponse response = new LessonResponse();
        response.setId(1L);
        response.setTitle("A");
        Page<LessonResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);

        when(request.getHeader("Authorization")).thenReturn(null);
        when(lessonRepository.findLessons(dto, "", PageRequest.of(0, 10))).thenReturn(page);
        when(attachDocumentLessonRepository.findAllByLessonId(1L))
                .thenReturn(List.of(AttachDocumentLesson.builder().id(5L).linkUrl("doc").build()));

        Page<LessonResponse> result = service.getLessons(request, dto, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertNotNull(result.getContent().get(0).getAttachDocumentLessons());
        assertEquals(1, result.getContent().get(0).getAttachDocumentLessons().size());
        assertNull(dto.getCategories());
    }
}
