package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.ECategoryCourse;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.Course;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.CourseRequest;
import com.doan2025.webtoeic.dto.response.CourseResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.CourseRepository;
import com.doan2025.webtoeic.repository.EnrollmentRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import com.doan2025.webtoeic.utils.NotiUtils;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ConvertUtil convertUtil;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private NotiUtils notiUtils;

    @InjectMocks
    private CourseServiceImpl service;

    @Mock
    private HttpServletRequest request;

    private User manager;
    private User consultant;

    @BeforeEach
    void init() {
        manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@test.com");
        manager.setRole(ERole.MANAGER);

        consultant = new User();
        consultant.setId(2L);
        consultant.setEmail("consultant@test.com");
        consultant.setRole(ERole.CONSULTANT);
    }

    @Test
    void createCourse_missingCategory_throwException() {
        CourseRequest req = new CourseRequest();
        req.setAuthorId(2L);
        req.setTitle("Java");
        req.setPrice(100L);

        assertThrows(WebToeicException.class, () -> service.createCourse(request, req));
    }

    @Test
    void createCourse_success_saveAndNotify() {
        CourseRequest req = new CourseRequest();
        req.setCategoryId(1);
        req.setAuthorId(2L);
        req.setTitle("Java core");
        req.setDescription("desc");
        req.setPrice(100L);
        req.setThumbnailUrl("thumb");

        User author = new User();
        author.setId(2L);
        author.setEmail("author@test.com");

        Course saved = Course.builder()
                .id(9L)
                .title("Java core")
                .categoryCourse(ECategoryCourse.LISTENING)
                .build();

        CourseResponse mapped = new CourseResponse();
        mapped.setId(9L);
        mapped.setTitle("Java core");

        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(jwtUtil.getEmailFromToken(request)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(userRepository.findUserOnlyStudent()).thenReturn(List.of(new User()));
        when(convertUtil.convertCourseToDto(request, saved)).thenReturn(mapped);

        CourseResponse result = service.createCourse(request, req);

        assertSame(mapped, result);
        verify(notiUtils).sendNoti(anyList(), any(), anyString(), anyString(), isNull());
    }

    @Test
    void getCourses_withoutBearer_setCategoriesNullAndCallRepo() {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(Collections.emptyList());

        when(request.getHeader("Authorization")).thenReturn(null);
        when(courseRepository.findCourses(dto, "", PageRequest.of(0, 10)))
                .thenReturn(Page.empty());

        service.getCourses(request, dto, PageRequest.of(0, 10));

        assertNull(dto.getCategories());
        verify(courseRepository).findCourses(dto, "", PageRequest.of(0, 10));
        verify(jwtUtil, never()).getEmailFromToken(any());
    }

    @Test
    void disableOrDeleteCourse_managerCanUpdateFlags() {
        CourseRequest req = new CourseRequest();
        req.setId(7L);
        req.setIsActive(false);
        req.setIsDelete(true);

        Course course = Course.builder().id(7L).isActive(true).isDelete(false).build();
        CourseResponse mapped = new CourseResponse();
        mapped.setId(7L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        when(convertUtil.convertCourseToDto(request, course)).thenReturn(mapped);

        CourseResponse result = service.disableOrDeleteCourse(request, req);

        assertSame(mapped, result);
        assertFalse(course.getIsActive());
        assertTrue(course.getIsDelete());
    }

    @Test
    void disableOrDeleteCourse_notManager_throwException() {
        CourseRequest req = new CourseRequest();
        req.setId(7L);

        Course course = Course.builder().id(7L).isActive(true).isDelete(false).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));

        assertThrows(WebToeicException.class, () -> service.disableOrDeleteCourse(request, req));
    }

    @Test
    void findByCourseBought_returnMappedPage() {
        Course course = Course.builder().id(1L).title("A").build();
        CourseResponse mapped = new CourseResponse();
        mapped.setId(1L);
        mapped.setTitle("A");

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(enrollmentRepository.findCourseByUser(consultant, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(course), PageRequest.of(0, 10), 1));
        when(convertUtil.convertCourseToDto(request, course)).thenReturn(mapped);

        Page<CourseResponse> result = service.findByCourseBought(request, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertSame(mapped, result.getContent().get(0));
    }

    @Test
    void getCourseDetail_success_returnMappedDto() {
        Course course = Course.builder().id(5L).title("C").build();
        CourseResponse mapped = new CourseResponse();
        mapped.setId(5L);

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(convertUtil.convertCourseToDto(request, course)).thenReturn(mapped);

        CourseResponse result = service.getCourseDetail(request, 5L);

        assertSame(mapped, result);
    }

    @Test
    void getCourses_withBearerToken_useJwtEmail() {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(List.of());
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.getEmailFromToken(request)).thenReturn("a@test.com");
        when(courseRepository.findCourses(dto, "a@test.com", PageRequest.of(0, 10))).thenReturn(Page.empty());

        service.getCourses(request, dto, PageRequest.of(0, 10));

        assertNull(dto.getCategories());
        verify(courseRepository).findCourses(dto, "a@test.com", PageRequest.of(0, 10));
    }

    @Test
    void getAllCourses_emptyCategories_setNullAndCallRepo() {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(List.of());
        when(courseRepository.findAllCourses(dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        service.getAllCourses(request, dto, PageRequest.of(0, 10));

        assertNull(dto.getCategories());
        verify(courseRepository).findAllCourses(dto, PageRequest.of(0, 10));
    }

    @Test
    void getOwnCourses_setEmailAndCallRepo() {
        SearchBaseDto dto = new SearchBaseDto();
        dto.setCategories(List.of());

        when(jwtUtil.getEmailFromToken(request)).thenReturn("owner@test.com");
        when(courseRepository.findOwnCourses(dto, "owner@test.com", PageRequest.of(0, 10))).thenReturn(Page.empty());

        service.getOwnCourses(request, dto, PageRequest.of(0, 10));

        assertEquals("owner@test.com", dto.getEmail());
        assertNull(dto.getCategories());
        verify(courseRepository).findOwnCourses(dto, "owner@test.com", PageRequest.of(0, 10));
    }

    @Test
    void updateCourse_success_updateAndReturnDto() {
        CourseRequest req = new CourseRequest();
        req.setId(11L);
        req.setTitle("new title");
        req.setDescription("new desc");
        req.setPrice(200L);
        req.setThumbnailUrl("new-thumb");
        req.setAuthorId(2L);
        req.setCategoryId(2);

        Course course = Course.builder().id(11L).title("old").build();
        User author = new User();
        author.setId(2L);
        CourseResponse mapped = new CourseResponse();
        mapped.setId(11L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(manager.getEmail());
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(courseRepository.findById(11L)).thenReturn(Optional.of(course));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(courseRepository.save(course)).thenReturn(course);
        when(convertUtil.convertCourseToDto(request, course)).thenReturn(mapped);

        CourseResponse result = service.updateCourse(request, req);

        assertSame(mapped, result);
        assertEquals("new title", course.getTitle());
        assertEquals(ECategoryCourse.SPEAKING, course.getCategoryCourse());
        assertSame(manager, course.getUpdatedBy());
    }
}
