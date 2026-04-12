package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.EClassStatus;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.ClassMember;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchClassDto;
import com.doan2025.webtoeic.dto.request.ClassRequest;
import com.doan2025.webtoeic.dto.response.ClassResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.ClassMemberRepository;
import com.doan2025.webtoeic.repository.ClassRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ConvertUtil convertUtil;

    @InjectMocks
    private ClassServiceImpl service;

    @Mock
    private HttpServletRequest request;

    private User student;
    private User teacher;
    private User consultant;

    @BeforeEach
    void init() {
        student = new User();
        student.setId(1L);
        student.setEmail("student@test.com");
        student.setRole(ERole.STUDENT);

        teacher = new User();
        teacher.setId(2L);
        teacher.setEmail("teacher@test.com");
        teacher.setCode("T-01");
        teacher.setRole(ERole.TEACHER);

        consultant = new User();
        consultant.setId(3L);
        consultant.setEmail("consultant@test.com");
        consultant.setRole(ERole.CONSULTANT);
        consultant.setCode("C-01");
    }

    @Test
    void get_studentNotInClass_throwException() {
        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));
        when(classMemberRepository.existsMemberInClass(10L, student.getId())).thenReturn(false);

        assertThrows(WebToeicException.class, () -> service.get(request, 10L));
    }

    @Test
    void getClasses_student_filterByMemberClassIds() {
        SearchClassDto dto = new SearchClassDto();
        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(10L).build();
        ClassResponse mapped = ClassResponse.builder().id(10L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(student.getEmail());
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classMemberRepository.findClassOfMember(student.getEmail())).thenReturn(List.of(10L));
        when(classRepository.filterClass(dto, List.of(10L), PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(clazz), PageRequest.of(0, 10), 1));
        when(convertUtil.convertClassToDto(request, clazz)).thenReturn(mapped);

        Page<ClassResponse> result = service.getClasses(request, dto, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertSame(mapped, result.getContent().get(0));
    }

    @Test
    void deleteClass_teacherOwner_setCancelled() {
        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(5L).teacher(teacher)
                .status(EClassStatus.PLANNING).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(classRepository.findById(5L)).thenReturn(Optional.of(clazz));

        service.deleteClass(List.of(5L), request);

        assertEquals(EClassStatus.CANCELLED, clazz.getStatus());
        verify(classRepository).save(clazz);
    }

    @Test
    void deleteClass_notOwnerAndNotConsultant_throwException() {
        User anotherTeacher = new User();
        anotherTeacher.setCode("T-99");

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(5L)
                .teacher(anotherTeacher).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacher.getEmail());
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(classRepository.findById(5L)).thenReturn(Optional.of(clazz));

        assertThrows(WebToeicException.class, () -> service.deleteClass(List.of(5L), request));
    }

    @Test
    void createClass_success_addTeacherAsMember() {
        ClassRequest req = ClassRequest.builder()
                .name("Class A")
                .title("Title")
                .subject("Toeic")
                .description("desc")
                .teacher(teacher.getId())
                .build();

        com.doan2025.webtoeic.domain.Class savedClass = com.doan2025.webtoeic.domain.Class.builder().id(8L)
                .teacher(teacher).build();
        ClassResponse mapped = ClassResponse.builder().id(8L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        when(classRepository.save(any(com.doan2025.webtoeic.domain.Class.class))).thenReturn(savedClass);
        when(convertUtil.convertClassToDto(request, savedClass)).thenReturn(mapped);

        ClassResponse result = service.createClass(request, req);

        assertSame(mapped, result);
        verify(classMemberRepository).save(any(ClassMember.class));
    }

    @Test
    void updateClass_success_updateTeacherAndStatus() {
        User newTeacher = new User();
        newTeacher.setId(9L);

        ClassRequest req = ClassRequest.builder().id(1L).teacher(9L).status(2).name("new").build();

        com.doan2025.webtoeic.domain.Class clazz = com.doan2025.webtoeic.domain.Class.builder().id(1L)
                .status(EClassStatus.PLANNING).build();
        ClassResponse mapped = ClassResponse.builder().id(1L).build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(consultant.getEmail());
        when(userRepository.findByEmail(consultant.getEmail())).thenReturn(Optional.of(consultant));
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        when(userRepository.findById(9L)).thenReturn(Optional.of(newTeacher));
        when(classRepository.save(clazz)).thenReturn(clazz);
        when(convertUtil.convertClassToDto(request, clazz)).thenReturn(mapped);

        ClassResponse result = service.updateClass(req, request);

        assertSame(mapped, result);
        assertEquals(newTeacher, clazz.getTeacher());
        assertEquals(EClassStatus.ONGOING, clazz.getStatus());
    }
}
