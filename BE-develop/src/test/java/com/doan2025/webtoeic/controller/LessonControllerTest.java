package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.LessonRequest;
import com.doan2025.webtoeic.dto.response.ApiResponse;
import com.doan2025.webtoeic.dto.response.LessonResponse;
import com.doan2025.webtoeic.service.LessonService;
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
class LessonControllerTest {

    @Mock
    private LessonService lessonService;

    @Mock
    private HttpServletRequest request;

    private LessonController controller;

    @BeforeEach
    void init() {
        controller = new LessonController(lessonService);
    }

    @Test
    void getLessonDetail_delegateService() {
        LessonResponse response = new LessonResponse();
        response.setId(3L);
        response.setTitle("L");
        when(lessonService.getDetail(request, 3L)).thenReturn(response);

        ApiResponse<?> result = controller.getLessonDetail(request, 3L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void getLessons_returnPage() {
        SearchBaseDto dto = new SearchBaseDto();
        Page<LessonResponse> page = Page.empty();
        when(lessonService.getLessons(request, dto, PageRequest.of(0, 10))).thenReturn(page);

        ApiResponse<?> result = controller.getLessons(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        assertSame(page, result.getData());
    }

    @Test
    void updateLesson_returnUpdateSuccess() {
        LessonRequest dto = LessonRequest.builder().build();
        LessonResponse response = new LessonResponse();
        response.setId(10L);
        when(lessonService.updateLesson(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.updateLesson(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void createLesson_returnCreateSuccess() {
        LessonRequest dto = LessonRequest.builder().build();
        LessonResponse response = new LessonResponse();
        response.setId(11L);
        when(lessonService.createLesson(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.createLesson(request, dto);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void getOwnLesson_returnGetSuccess() {
        SearchBaseDto dto = new SearchBaseDto();
        when(lessonService.getOwnLessons(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getOwnLesson(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(lessonService).getOwnLessons(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void getAllLesson_returnGetSuccess() {
        SearchBaseDto dto = new SearchBaseDto();
        when(lessonService.getAllLessons(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getAllLesson(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(lessonService).getAllLessons(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void disableOrDeleteLesson_returnUpdateSuccess() {
        LessonRequest dto = LessonRequest.builder().build();
        LessonResponse response = new LessonResponse();
        response.setId(12L);
        when(lessonService.disableOrDelete(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.disableOrDeleteLesson(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }
}
