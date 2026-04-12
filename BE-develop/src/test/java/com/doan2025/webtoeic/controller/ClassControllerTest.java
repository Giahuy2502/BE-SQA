package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.SearchClassDto;
import com.doan2025.webtoeic.dto.SearchMemberInClassDto;
import com.doan2025.webtoeic.dto.SearchNotificationInClassDto;
import com.doan2025.webtoeic.dto.SearchScheduleSto;
import com.doan2025.webtoeic.dto.SearchSubmitExerciseDto;
import com.doan2025.webtoeic.dto.request.AttendanceRequest;
import com.doan2025.webtoeic.dto.request.ClassNotificationRequest;
import com.doan2025.webtoeic.dto.request.ClassScheduleRequest;
import com.doan2025.webtoeic.dto.request.ClassRequest;
import com.doan2025.webtoeic.dto.request.SubmitExerciseRequest;
import com.doan2025.webtoeic.dto.response.ApiResponse;
import com.doan2025.webtoeic.dto.response.ClassResponse;
import com.doan2025.webtoeic.dto.response.SubmitExerciseResponse;
import com.doan2025.webtoeic.service.*;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassControllerTest {

    @Mock
    private ClassService classService;
    @Mock
    private ClassMemberService classMemberService;
    @Mock
    private ClassScheduleService classScheduleService;
    @Mock
    private AttendanceService attendanceService;
    @Mock
    private ClassNotificationService classNotificationService;
    @Mock
    private SubmitExersiceService submitExersiceService;

    @Mock
    private HttpServletRequest request;

    private ClassController controller;

    @BeforeEach
    void init() {
        controller = new ClassController(classService, classMemberService, classScheduleService,
                attendanceService, classNotificationService, submitExersiceService);
    }

    @Test
    void getDetailExerciseInNotification_returnData() {
        SubmitExerciseResponse response = SubmitExerciseResponse.builder().id(2L).build();
        when(submitExersiceService.getDetailSubmitExercise(request, 2L)).thenReturn(response);

        ApiResponse<?> result = controller.getDetailExerciseInNotification(request, 2L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void createSubmitExercise_returnCreateSuccess() {
        SubmitExerciseRequest dto = new SubmitExerciseRequest();
        SubmitExerciseResponse response = SubmitExerciseResponse.builder().id(5L).build();
        when(submitExersiceService.createSubmitExercise(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.createSubmitExercise(request, dto);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void filterClass_delegateToService() {
        SearchClassDto dto = new SearchClassDto();
        when(classService.getClasses(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.filterClass(dto, request, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(classService).getClasses(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void createClass_returnCreateSuccess() {
        ClassRequest dto = ClassRequest.builder().build();
        ClassResponse response = ClassResponse.builder().id(10L).build();
        when(classService.createClass(request, dto)).thenReturn(response);

        ApiResponse<?> result = controller.createClass(request, dto);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        assertSame(response, result.getData());
    }

    @Test
    void deleteClass_returnDeleteSuccess() {
        ApiResponse<Void> result = controller.deleteClass(request, List.of(1L, 2L));

        assertEquals(ResponseCode.DELETE_SUCCESS.getCode(), result.getCode());
        verify(classService).deleteClass(List.of(1L, 2L), request);
    }

    @Test
    void getListSubmitExercise_returnGetSuccess() {
        SearchSubmitExerciseDto dto = new SearchSubmitExerciseDto();
        when(submitExersiceService.getListSubmitExercise(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getListSubmitExercise(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(submitExersiceService).getListSubmitExercise(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void deleteOrCancelSubmitExercise_returnDeleteSuccess() {
        SubmitExerciseRequest dto = new SubmitExerciseRequest();
        when(submitExersiceService.deleteOrCancelSubmitExercise(request, dto)).thenReturn(null);

        ApiResponse<?> result = controller.deleteOrCancelSubmitExercise(request, dto);

        assertEquals(ResponseCode.DELETE_SUCCESS.getCode(), result.getCode());
        verify(submitExersiceService).deleteOrCancelSubmitExercise(request, dto);
    }

    @Test
    void updateSubmitExercise_returnUpdateSuccess() {
        SubmitExerciseRequest dto = new SubmitExerciseRequest();
        when(submitExersiceService.updateSubmitExercise(request, dto)).thenReturn(null);

        ApiResponse<?> result = controller.updateSubmitExercise(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(submitExersiceService).updateSubmitExercise(request, dto);
    }

    @Test
    void getDetailNotificationInClass_delegateService() {
        when(classNotificationService.getDetailNotificationInClass(request, 8L)).thenReturn(null);

        ApiResponse<?> result = controller.getDetailNotificationInClass(request, 8L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(classNotificationService).getDetailNotificationInClass(request, 8L);
    }

    @Test
    void getListNotificationInClass_delegateService() {
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        when(classNotificationService.getListNotificationInClass(request, dto, PageRequest.of(0, 10)))
                .thenReturn(Page.empty());

        ApiResponse<?> result = controller.getListNotificationInClass(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.DELETE_SUCCESS.getCode(), result.getCode());
        verify(classNotificationService).getListNotificationInClass(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void disableOrDeleteNotificationInClass_delegateService() {
        ClassNotificationRequest dto = new ClassNotificationRequest();
        when(classNotificationService.disableOrDeleteNotificationInClass(request, dto)).thenReturn(null);

        ApiResponse<?> result = controller.disableOrDeleteNotificationInClass(request, dto);

        assertEquals(ResponseCode.DELETE_SUCCESS.getCode(), result.getCode());
        verify(classNotificationService).disableOrDeleteNotificationInClass(request, dto);
    }

    @Test
    void updateNotificationInClass_delegateService() {
        ClassNotificationRequest dto = new ClassNotificationRequest();
        when(classNotificationService.updateNotificationInClass(request, dto)).thenReturn(null);

        ApiResponse<?> result = controller.updateNotificationInClass(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(classNotificationService).updateNotificationInClass(request, dto);
    }

    @Test
    void createNotificationInClass_delegateService() {
        ClassNotificationRequest dto = new ClassNotificationRequest();
        when(classNotificationService.createNotificationInClass(request, dto)).thenReturn(null);

        ApiResponse<?> result = controller.createNotificationInClass(request, dto);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(classNotificationService).createNotificationInClass(request, dto);
    }

    @Test
    void attendance_returnCreateSuccess() {
        ApiResponse<Void> result = controller.attendance(request, List.of(new AttendanceRequest()));

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(attendanceService).attendance(eq(request), anyList());
    }

    @Test
    void updateAttendance_returnUpdateSuccess() {
        ApiResponse<Void> result = controller.updateAttendance(request, List.of(new AttendanceRequest()));

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(attendanceService).updateAttendance(eq(request), anyList());
    }

    @Test
    void getStudentInClass_delegateService() {
        SearchMemberInClassDto dto = SearchMemberInClassDto.builder().build();
        when(classMemberService.getMemberInClass(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getStudentInClass(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(classMemberService).getMemberInClass(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void getScheduleDetailInClass_delegateService() {
        when(classScheduleService.getScheduleDetail(request, 7L)).thenReturn(null);

        ApiResponse<?> result = controller.getScheduleDetailInClass(request, 7L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(classScheduleService).getScheduleDetail(request, 7L);
    }

    @Test
    void getScheduleInClass_delegateService() {
        SearchScheduleSto dto = new SearchScheduleSto();
        when(classScheduleService.getClassSchedule(request, dto, PageRequest.of(0, 10))).thenReturn(Page.empty());

        ApiResponse<?> result = controller.getScheduleInClass(request, dto, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(classScheduleService).getClassSchedule(request, dto, PageRequest.of(0, 10));
    }

    @Test
    void cancelledScheduleInClass_returnCancelledSuccess() {
        ApiResponse<?> result = controller.cancelledScheduleInClass(request, List.of(1L));

        assertEquals(ResponseCode.CANCELLED_SUCCESS.getCode(), result.getCode());
        verify(classScheduleService).cancelledScheduleInClass(request, List.of(1L));
    }

    @Test
    void updateScheduleInClass_delegateService() {
        ClassScheduleRequest dto = new ClassScheduleRequest();
        when(classScheduleService.updateScheduleInClass(request, dto)).thenReturn(null);

        ApiResponse<?> result = controller.updateScheduleInClass(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(classScheduleService).updateScheduleInClass(request, dto);
    }

    @Test
    void createScheduleInClass_delegateService() {
        List<ClassScheduleRequest> dtos = List.of(new ClassScheduleRequest());
        when(classScheduleService.createScheduleInClass(request, dtos)).thenReturn(List.of());

        ApiResponse<?> result = controller.createScheduleInClass(request, dtos);

        assertEquals(ResponseCode.CREATE_SUCCESS.getCode(), result.getCode());
        verify(classScheduleService).createScheduleInClass(request, dtos);
    }

    @Test
    void removeUserFromClass_returnDeleteSuccess() {
        ClassRequest dto = ClassRequest.builder().build();

        ApiResponse<Void> result = controller.removeUserFromClass(request, dto);

        assertEquals(ResponseCode.DELETE_SUCCESS.getCode(), result.getCode());
        verify(classMemberService).removeUserFromClass(request, dto);
    }

    @Test
    void addUserToClass_returnUpdateSuccess() {
        ClassRequest dto = ClassRequest.builder().build();

        ApiResponse<Void> result = controller.addUserToClass(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(classMemberService).addUserToClass(request, dto);
    }

    @Test
    void getDetail_delegateService() {
        when(classService.get(request, 5L)).thenReturn(ClassResponse.builder().id(5L).build());

        ApiResponse<?> result = controller.getDetail(request, 5L);

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), result.getCode());
        verify(classService).get(request, 5L);
    }

    @Test
    void updateClass_returnUpdateSuccess() {
        ClassRequest dto = ClassRequest.builder().build();
        when(classService.updateClass(dto, request)).thenReturn(ClassResponse.builder().id(12L).build());

        ApiResponse<?> result = controller.updateClass(request, dto);

        assertEquals(ResponseCode.UPDATE_SUCCESS.getCode(), result.getCode());
        verify(classService).updateClass(dto, request);
    }

    @Test
    void attendanceOverviewEndpoints_delegateService() {
        when(classScheduleService.detailStatisticAttendance(request, 1L, PageRequest.of(0, 10)))
                .thenReturn(Page.empty());
        when(classScheduleService.overviewStatisticAttendance(request, 1L, PageRequest.of(0, 10)))
                .thenReturn(Page.empty());
        when(classScheduleService.overviewStudentAttendance(request, 1L, PageRequest.of(0, 10)))
                .thenReturn(Page.empty());

        ApiResponse<?> detail = controller.detailStatisticAttendance(request, 1L, PageRequest.of(0, 10));
        ApiResponse<?> overview = controller.overviewStatisticAttendance(request, 1L, PageRequest.of(0, 10));
        ApiResponse<?> student = controller.overviewStudentAttendance(request, 1L, PageRequest.of(0, 10));

        assertEquals(ResponseCode.GET_SUCCESS.getCode(), detail.getCode());
        assertEquals(ResponseCode.GET_SUCCESS.getCode(), overview.getCode());
        assertEquals(ResponseCode.GET_SUCCESS.getCode(), student.getCode());
    }
}
