package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.SearchQuizDto;
import com.doan2025.webtoeic.dto.SearchSubmittedDto;
import com.doan2025.webtoeic.dto.request.QuizRequest;
import com.doan2025.webtoeic.dto.request.SharedQuizRequest;
import com.doan2025.webtoeic.dto.request.SubmitRequest;
import com.doan2025.webtoeic.dto.response.ApiResponse;
import com.doan2025.webtoeic.dto.response.QuizResponse;
import com.doan2025.webtoeic.service.QuizService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizControllerTest {

    @Mock
    private QuizService quizService;

    @Mock
    private HttpServletRequest request;

    private QuizController controller;

    @BeforeEach
    void init() {
        controller = new QuizController(quizService);
    }

    @Test
    void submitQuizInClass_nullRequestBody_convertToEmptyList() {
        ApiResponse<Void> result = controller.submitQuizInClass(request, 1L, 2L, "note", null);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).submitQuiz(eq(request), eq(1L), argThat(list -> list != null && list.isEmpty()), eq(2L),
                eq("note"));
    }

    @Test
    void getDetailQuizz_delegateToService() {
        QuizResponse response = QuizResponse.builder().id(3L).title("Q").build();
        when(quizService.getQuiz(request, 3L)).thenReturn(response);

        ApiResponse<?> result = controller.getDetailQuizz(request, 3L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void searchQuizz_delegatePaging() {
        SearchQuizDto dto = new SearchQuizDto();
        when(quizService.getQuizes(request, dto, PageRequest.of(0, 10)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        ApiResponse<?> result = controller.searchQuizz(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).getQuizes(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void updateTimeQuizInClass_returnUpdateSuccess() {
        SharedQuizRequest dto = new SharedQuizRequest();

        ApiResponse<Void> result = controller.updateTimeQuizInClass(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).updateQuizInClass(request, dto);
    }

    @Test
    void createQuizz_returnCreateSuccess() {
        QuizRequest req = new QuizRequest();
        QuizResponse mapped = QuizResponse.builder().id(10L).build();
        when(quizService.createQuiz(request, req)).thenReturn(mapped);

        ApiResponse<?> result = controller.createQuizz(request, req);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        assertSame(mapped, result.getData());
    }

    @Test
    void statisticDetailQuizInClass_delegateService() {
        SearchSubmittedDto dto = new SearchSubmittedDto();
        when(quizService.statisticDetailQuizInClass(request, 1L, 2L, 5L, dto)).thenReturn(null);

        ApiResponse<?> result = controller.statisticDetailQuizInClass(request, 1L, 2L, 5L, dto);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).statisticDetailQuizInClass(request, 1L, 2L, 5L, dto);
    }

    @Test
    void statisticOverviewQuizInClass_delegateService() {
        SearchQuizDto dto = new SearchQuizDto();
        when(quizService.statisticOverviewQuizInClass(request, 2L, 6L, dto)).thenReturn(null);

        ApiResponse<?> result = controller.statisticOverviewQuizInClass(request, 2L, 6L, dto);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).statisticOverviewQuizInClass(request, 2L, 6L, dto);
    }

    @Test
    void viewSubmittedQuizInClass_delegateService() {
        when(quizService.getDetailSubmitQuiz(request, 9L)).thenReturn(null);

        ApiResponse<?> result = controller.viewSubmittedQuizInClass(request, 9L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).getDetailSubmitQuiz(request, 9L);
    }

    @Test
    void searchSubmittedQuizInClass_delegateService() {
        SearchSubmittedDto dto = new SearchSubmittedDto();
        when(quizService.getListSubmitQuiz(request, 3L, 4L, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<Page<?>> result = controller.searchSubmittedQuizInClass(request, 3L, 4L, dto,
                PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).getListSubmitQuiz(request, 3L, 4L, dto, PageRequest.of(0, 10));
    }

    @Test
    void overviewStudentSubmitInClass_delegateService() {
        when(quizService.overviewStudentSubmitInClass(request, 6L, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<Page<?>> result = controller.overviewStudentSubmitInClass(request, 6L, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).overviewStudentSubmitInClass(request, 6L, PageRequest.of(0, 10));
    }

    @Test
    void submitQuizInClass_hasBody_delegateDirectly() {
        SubmitRequest submitRequest = new SubmitRequest();

        ApiResponse<Void> result = controller.submitQuizInClass(request, 1L, 2L, "des", List.of(submitRequest));

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).submitQuiz(request, 1L, List.of(submitRequest), 2L, "des");
    }

    @Test
    void listQuizInClass_delegateService() {
        SearchQuizDto dto = new SearchQuizDto();
        when(quizService.getListQuizInClass(request, 2L, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.listQuizInClass(request, 2L, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(quizService).getListQuizInClass(request, 2L, dto, PageRequest.of(0, 10));
    }

    @Test
    void pullQuizToClass_returnUpdateSuccess() {
        SharedQuizRequest dto = new SharedQuizRequest();

        ApiResponse<Void> result = controller.pullQuizToClass(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).pullQuizToClass(request, dto);
    }

    @Test
    void updateQuizz_delegateService() {
        QuizRequest quizRequest = new QuizRequest();
        when(quizService.updateQuiz(request, quizRequest)).thenReturn(null);

        ApiResponse<?> result = controller.updateQuizz(request, quizRequest);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).updateQuiz(request, quizRequest);
    }

    @Test
    void convertBankToQuiz_delegateService() {
        when(quizService.convertBankToQuiz(request, 99L)).thenReturn(null);

        ApiResponse<?> result = controller.convertBankToQuiz(request, 99L);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).convertBankToQuiz(request, 99L);
    }

    @Test
    void addQuestionsToQuiz_delegateService() {
        QuizRequest quizRequest = new QuizRequest();
        when(quizService.addQuestionToQuiz(request, quizRequest)).thenReturn(null);

        ApiResponse<?> result = controller.addQuestionsToQuiz(request, quizRequest);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).addQuestionToQuiz(request, quizRequest);
    }

    @Test
    void removeQuestionsToQuiz_delegateService() {
        QuizRequest quizRequest = new QuizRequest();
        when(quizService.removeQuestionFromQuiz(request, quizRequest)).thenReturn(null);

        ApiResponse<?> result = controller.removeQuestionsToQuiz(request, quizRequest);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(quizService).removeQuestionFromQuiz(request, quizRequest);
    }
}
