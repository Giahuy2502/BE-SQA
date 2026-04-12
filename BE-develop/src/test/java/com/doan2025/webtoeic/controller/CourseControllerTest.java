package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.CourseRequest;
import com.doan2025.webtoeic.dto.response.ApiResponse;
import com.doan2025.webtoeic.dto.response.CourseResponse;
import com.doan2025.webtoeic.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock
    private CourseService courseService;

    @Mock
    private HttpServletRequest request;

    private CourseController controller;

    @BeforeEach
    void setup() {
        controller = new CourseController(courseService);
    }

    @Test
    void getCourse_returnGetSuccess() {
        CourseResponse response = new CourseResponse();
        response.setId(1L);
        response.setTitle("Java");
        when(courseService.getCourseDetail(request, 1L)).thenReturn(response);

        ApiResponse<CourseResponse> result = controller.getCourse(request, 1L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void getCourses_delegateAndReturnData() {
        SearchBaseDto dto = new SearchBaseDto();
        Page<CourseResponse> page = Page.empty();
        when(courseService.getCourses(request, dto, PageRequest.of(0, 10))).thenReturn(page);

        ApiResponse<?> result = controller.getCourses(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        assertSame(page, result.getData());
    }

    @Test
    void updateStatusCourse_returnUpdateSuccess() {
        CourseRequest dto = new CourseRequest();
        CourseResponse response = new CourseResponse();
        response.setId(7L);
        when(courseService.disableOrDeleteCourse(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.updateStatusCourse(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void createCourse_returnCreateSuccess() {
        CourseRequest dto = new CourseRequest();
        CourseResponse response = new CourseResponse();
        response.setId(7L);
        when(courseService.createCourse(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.createCourse(request, dto);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void getBoughtCourse_returnGetSuccess() {
        when(courseService.findByCourseBought(request, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<Page<CourseResponse>> result = controller.getBoughtCourse(request, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(courseService).findByCourseBought(request, PageRequest.of(0, 10));
    }

    @Test
    void getAllCourses_returnGetSuccess() {
        SearchBaseDto dto = new SearchBaseDto();
        when(courseService.getAllCourses(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getAllCourses(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(courseService).getAllCourses(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void getOwnCourses_returnGetSuccess() {
        SearchBaseDto dto = new SearchBaseDto();
        when(courseService.getOwnCourses(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getOwnCourses(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(courseService).getOwnCourses(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void updateInfoCourse_returnUpdateSuccess() {
        CourseRequest dto = new CourseRequest();
        CourseResponse response = new CourseResponse();
        response.setId(9L);
        when(courseService.updateCourse(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.updateInfoCourse(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }
}
