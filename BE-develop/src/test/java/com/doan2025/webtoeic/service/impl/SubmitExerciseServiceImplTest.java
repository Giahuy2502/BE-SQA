package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.ClassNotification;
import com.doan2025.webtoeic.domain.SubmitExercise;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchSubmitExerciseDto;
import com.doan2025.webtoeic.dto.request.SubmitExerciseRequest;
import com.doan2025.webtoeic.dto.response.SubmitExerciseResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.ClassMemberRepository;
import com.doan2025.webtoeic.repository.ClassNotificationRepository;
import com.doan2025.webtoeic.repository.SubmitExerciseRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
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

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitExerciseServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ConvertUtil convertUtil;
    @Mock
    private ClassNotificationRepository classNotificationRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private SubmitExerciseRepository submitExerciseRepository;

    @InjectMocks
    private SubmitExerciseServiceImpl service;

    @Mock
    private HttpServletRequest request;

    private User teacher;
    private User student;

    @BeforeEach
    void init() {
        teacher = new User();
        teacher.setId(1L);
        teacher.setEmail("teacher@test.com");
        teacher.setRole(ERole.TEACHER);

        student = new User();
        student.setId(2L);
        student.setEmail("student@test.com");
        student.setRole(ERole.STUDENT);
    }

    @Test
    void getDetailSubmitExercise_teacherInClass_returnData() {
        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();
        ClassNotification noti = ClassNotification.builder().clazz(clazz).build();
        SubmitExercise submit = SubmitExercise.builder().id(9L).classNotification(noti).createdBy(student).build();
        SubmitExerciseResponse response = SubmitExerciseResponse.builder().id(9L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(submitExerciseRepository.findById(9L)).thenReturn(Optional.of(submit));
        when(classMemberRepository.existsMemberInClass(10L, teacher.getId())).thenReturn(true);
        when(convertUtil.convertSubmitExerciseToDto(request, submit)).thenReturn(response);

        SubmitExerciseResponse result = service.getDetailSubmitExercise(request, 9L);

        assertSame(response, result);
    }

    @Test
    void getDetailSubmitExercise_notOwnerAndNotTeacher_throwException() {
        User stranger = new User();
        stranger.setId(100L);
        stranger.setEmail("stranger@test.com");
        stranger.setRole(ERole.STUDENT);

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();
        ClassNotification noti = ClassNotification.builder().clazz(clazz).build();
        SubmitExercise submit = SubmitExercise.builder().id(9L).classNotification(noti).createdBy(student).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(stranger.getEmail());
        when(userRepository.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        when(submitExerciseRepository.findById(9L)).thenReturn(Optional.of(submit));

        assertThrows(WebToeicException.class, () -> service.getDetailSubmitExercise(request, 9L));
    }

    @Test
    void createSubmitExercise_beforeStart_throwException() {
        SubmitExerciseRequest req = new SubmitExerciseRequest();
        req.setNotificationId(7L);

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();
        ClassNotification noti = ClassNotification.builder()
                .id(7L)
                .clazz(clazz)
                .fromDate(new Date(System.currentTimeMillis() + 3600_000))
                .toDate(new Date(System.currentTimeMillis() + 7200_000))
                .build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classNotificationRepository.findById(7L)).thenReturn(Optional.of(noti));
        when(classMemberRepository.existsMemberInClass(10L, student.getId())).thenReturn(true);

        assertThrows(WebToeicException.class, () -> service.createSubmitExercise(request, req));
    }

    @Test
    void createSubmitExercise_valid_disableOldAndCreateNew() {
        SubmitExerciseRequest req = new SubmitExerciseRequest();
        req.setNotificationId(8L);
        req.setLinkUrl("https://example.com/work");

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();
        ClassNotification noti = ClassNotification.builder()
                .id(8L)
                .clazz(clazz)
                .fromDate(new Date(System.currentTimeMillis() - 3600_000))
                .toDate(new Date(System.currentTimeMillis() + 3600_000))
                .build();

        SubmitExercise oldSubmit = SubmitExercise.builder().id(1L).isActive(true).build();
        SubmitExercise saved = SubmitExercise.builder().id(2L).build();
        SubmitExerciseResponse response = SubmitExerciseResponse.builder().id(2L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classNotificationRepository.findById(8L)).thenReturn(Optional.of(noti));
        when(classMemberRepository.existsMemberInClass(10L, student.getId())).thenReturn(true);
        when(submitExerciseRepository.findByClassNotificationIdAndCreatedById(8L, student.getId()))
                .thenReturn(List.of(oldSubmit));
        when(submitExerciseRepository.save(any(SubmitExercise.class))).thenReturn(saved);
        when(convertUtil.convertSubmitExerciseToDto(request, saved)).thenReturn(response);

        SubmitExerciseResponse result = service.createSubmitExercise(request, req);

        assertSame(response, result);
        verify(submitExerciseRepository, atLeast(2)).save(any(SubmitExercise.class));
    }

    @Test
    void updateSubmitExercise_notOwner_throwException() {
        SubmitExerciseRequest req = new SubmitExerciseRequest();
        req.setSubmitId(9L);

        User owner = new User();
        owner.setId(99L);

        SubmitExercise submit = SubmitExercise.builder().id(9L).createdBy(owner).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(submitExerciseRepository.findById(9L)).thenReturn(Optional.of(submit));

        assertThrows(WebToeicException.class, () -> service.updateSubmitExercise(request, req));
    }

    @Test
    void deleteOrCancelSubmitExercise_ownerInTimeWindow_success() {
        SubmitExerciseRequest req = new SubmitExerciseRequest();
        req.setSubmitId(9L);
        req.setIsActive(false);
        req.setIsDelete(true);

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(1L).build();
        ClassNotification noti = ClassNotification.builder()
                .clazz(clazz)
                .fromDate(new Date(System.currentTimeMillis() - 3600_000))
                .toDate(new Date(System.currentTimeMillis() + 3600_000))
                .build();

        SubmitExercise submit = SubmitExercise.builder()
                .id(9L)
                .createdBy(student)
                .classNotification(noti)
                .isActive(true)
                .isDelete(false)
                .build();

        SubmitExerciseResponse response = SubmitExerciseResponse.builder().id(9L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(submitExerciseRepository.findById(9L)).thenReturn(Optional.of(submit));
        when(submitExerciseRepository.save(any(SubmitExercise.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(convertUtil.convertSubmitExerciseToDto(request, submit)).thenReturn(response);

        SubmitExerciseResponse result = service.deleteOrCancelSubmitExercise(request, req);

        assertSame(response, result);
        assertFalse(submit.getIsActive());
        assertTrue(submit.getIsDelete());
    }

    @Test
    void getListSubmitExercise_teacherInClass_returnsPage() {
        SearchSubmitExerciseDto dto = new SearchSubmitExerciseDto();
        dto.setNotificationId(11L);

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();
        ClassNotification noti = ClassNotification.builder().id(11L).clazz(clazz).build();
        SubmitExercise item = SubmitExercise.builder().id(1L).build();
        SubmitExerciseResponse mapped = SubmitExerciseResponse.builder().id(1L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(classNotificationRepository.findById(11L)).thenReturn(Optional.of(noti));
        when(classMemberRepository.existsMemberInClass(10L, teacher.getId())).thenReturn(true);
        when(submitExerciseRepository.findByClassNotificationId(dto, PageRequest.of(0, 10), null))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));
        when(convertUtil.convertSubmitExerciseToDto(request, item)).thenReturn(mapped);

        Page<SubmitExerciseResponse> result = service.getListSubmitExercise(request, dto, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertSame(mapped, result.getContent().get(0));
    }
}
