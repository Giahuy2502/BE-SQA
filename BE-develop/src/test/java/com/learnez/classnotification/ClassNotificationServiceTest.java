package com.learnez.classnotification;

import com.doan2025.webtoeic.constants.enums.EClassNotificationType;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.AttachDocumentClass;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.domain.ClassNotification;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchNotificationInClassDto;
import com.doan2025.webtoeic.dto.request.ClassNotificationRequest;
import com.doan2025.webtoeic.dto.response.AttachDocumentClassResponse;
import com.doan2025.webtoeic.dto.response.ClassNotificationResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.AttachDocumentClassRepository;
import com.doan2025.webtoeic.repository.ClassMemberRepository;
import com.doan2025.webtoeic.repository.ClassNotificationRepository;
import com.doan2025.webtoeic.repository.ClassRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.service.impl.ClassNotificationServiceImpl;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import com.doan2025.webtoeic.utils.NotiUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClassNotificationServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ConvertUtil convertUtil;

    @Mock
    private ClassNotificationRepository classNotificationRepository;

    @Mock
    private AttachDocumentClassRepository attachDocumentClassRepository;

    @Mock
    private ClassMemberRepository classMemberRepository;

    @Mock
    private NotiUtils notiUtils;

    @InjectMocks
    private ClassNotificationServiceImpl classNotificationService;

    @Test
    public void TC_CLSNOTI_001_getDetail_studentMember_shouldReturnDetail() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "student@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User student = new User();
        student.setId(11L);
        student.setEmail(email);
        student.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(student));

        Class clazz = new Class();
        clazz.setId(101L);

        ClassNotification notification = new ClassNotification();
        notification.setId(1001L);
        notification.setClazz(clazz);
        notification.setDescription("Class announcement");
        when(classNotificationRepository.findById(1001L)).thenReturn(Optional.of(notification));
        when(classMemberRepository.existsMemberInClass(101L, 11L)).thenReturn(true);

        AttachDocumentClass attachment = new AttachDocumentClass();
        attachment.setId(501L);
        attachment.setLinkUrl("https://example.com/doc.pdf");
        when(attachDocumentClassRepository.findByClassNotificationId(1001L)).thenReturn(List.of(attachment));

        ClassNotificationResponse expected = ClassNotificationResponse.builder()
                .id(1001L)
                .description("Class announcement")
                .attachDocumentClasses(List.of(new AttachDocumentClassResponse()))
                .build();
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList())).thenReturn(expected);

        ClassNotificationResponse result = classNotificationService.getDetailNotificationInClass(request, 1001L);

        assertEquals(expected, result);
        verify(classNotificationRepository).findById(1001L);
        verify(classMemberRepository).existsMemberInClass(101L, 11L);
        verify(convertUtil).convertClassNotificationToDto(eq(request), eq(notification), anyList());
    }

    @Test
    public void TC_CLSNOTI_002_getDetail_studentNotMember_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "student2@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User student = new User();
        student.setId(12L);
        student.setEmail(email);
        student.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(student));

        Class clazz = new Class();
        clazz.setId(102L);

        ClassNotification notification = new ClassNotification();
        notification.setId(1002L);
        notification.setClazz(clazz);
        when(classNotificationRepository.findById(1002L)).thenReturn(Optional.of(notification));
        when(classMemberRepository.existsMemberInClass(102L, 12L)).thenReturn(false);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.getDetailNotificationInClass(request, 1002L));
        verify(convertUtil, never()).convertClassNotificationToDto(any(), any(), anyList());
    }

    // TC-CLSNOTI-010: getDetail with missing user should throw NOT_EXISTED.
    @Test
    public void TC_CLSNOTI_010_getDetail_userNotFound_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "missing-user@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classNotificationService.getDetailNotificationInClass(request, 123L));
        verify(classNotificationRepository, never()).findById(anyLong());
    }

    @Test
    public void TC_CLSNOTI_003_getList_manager_shouldReturnMappedPage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "manager@example.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User manager = new User();
        manager.setId(21L);
        manager.setEmail(email);
        manager.setRole(ERole.MANAGER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(manager));

        Class clazz = new Class();
        clazz.setId(201L);
        when(classRepository.findById(201L)).thenReturn(Optional.of(clazz));

        ClassNotification notification = new ClassNotification();
        notification.setId(2001L);
        notification.setClazz(clazz);
        notification.setDescription("Exam notice");
        Page<ClassNotification> page = new PageImpl<>(List.of(notification));
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(201L);
        when(classNotificationRepository.findByClazzId(eq(dto), eq("MANAGER"), any(Pageable.class))).thenReturn(page);

        ClassNotificationResponse mapped = ClassNotificationResponse.builder().id(2001L).description("Exam notice")
                .build();
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList())).thenReturn(mapped);

        Page<ClassNotificationResponse> result = classNotificationService.getListNotificationInClass(request, dto,
                Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        verify(classNotificationRepository).findByClazzId(eq(dto), eq("MANAGER"), any(Pageable.class));
    }

    // TC-CLSNOTI-012: getList with missing class should throw NOT_EXISTED.
    @Test
    public void TC_CLSNOTI_012_getList_classNotFound_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "manager-missing@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User manager = new User();
        manager.setId(23L);
        manager.setEmail(email);
        manager.setRole(ERole.MANAGER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(manager));

        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(9999L);
        when(classRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classNotificationService.getListNotificationInClass(request, dto, Pageable.unpaged()));
        verify(classNotificationRepository, never()).findByClazzId(any(), anyString(), any());
    }

    @Test
    public void TC_CLSNOTI_004_createNotification_teacher_shouldSaveAndSend() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(31L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(301L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(301L)).thenReturn(Optional.of(clazz));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(301L);
        req.setDescription("Homework update");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());
        req.setIsPin(true);
        req.setUrlAttachment(List.of("https://example.com/a.pdf", "https://example.com/b.pdf"));

        doAnswer(invocation -> {
            ClassNotification notification = invocation.getArgument(0);
            notification.setId(4001L);
            notification.setIsActive(true);
            notification.setIsDelete(false);
            return notification;
        }).when(classNotificationRepository).save(any(ClassNotification.class));

        when(attachDocumentClassRepository.findByClassNotificationId(4001L)).thenReturn(List.of());
        when(classMemberRepository.findMembersInClass(301L)).thenReturn(List.of(teacher));

        ClassNotificationResponse expected = ClassNotificationResponse.builder().id(4001L)
                .description("Homework update").build();
        when(convertUtil.convertClassNotificationToDto(eq(request), any(ClassNotification.class), anyList()))
                .thenReturn(expected);

        ClassNotificationResponse result = classNotificationService.createNotificationInClass(request, req);

        assertEquals(expected, result);
        ArgumentCaptor<ClassNotification> captor = ArgumentCaptor.forClass(ClassNotification.class);
        verify(classNotificationRepository).save(captor.capture());
        assertEquals("Homework update", captor.getValue().getDescription());
        assertEquals(EClassNotificationType.NOTIFICATION, captor.getValue().getTypeNotification());
        assertEquals(teacherEmail, captor.getValue().getCreatedBy().getEmail());
        verify(attachDocumentClassRepository, times(2)).save(any(AttachDocumentClass.class));
        verify(notiUtils).sendNoti(eq(List.of(teacher)), any(), anyString(), anyString(), eq(301L));
    }

    // TC-CLSNOTI-016: getDetail for non-student role should bypass membership
    // check.
    @Test
    public void TC_CLSNOTI_016_getDetail_manager_shouldBypassMembershipCheck() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "manager-detail@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User manager = new User();
        manager.setId(91L);
        manager.setEmail(email);
        manager.setRole(ERole.MANAGER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(manager));

        Class clazz = new Class();
        clazz.setId(901L);
        ClassNotification notification = new ClassNotification();
        notification.setId(9001L);
        notification.setClazz(clazz);
        when(classNotificationRepository.findById(9001L)).thenReturn(Optional.of(notification));
        when(attachDocumentClassRepository.findByClassNotificationId(9001L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9001L).build());

        ClassNotificationResponse result = classNotificationService.getDetailNotificationInClass(request, 9001L);

        assertNotNull(result);
        verify(classMemberRepository, never()).existsMemberInClass(anyLong(), anyLong());
    }

    // TC-CLSNOTI-017: getList for student not in class must throw NOT_PERMISSION.
    @Test
    public void TC_CLSNOTI_017_getList_studentNotMember_shouldThrowNotPermission() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "student-not-member@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User student = new User();
        student.setId(92L);
        student.setEmail(email);
        student.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(student));

        Class clazz = new Class();
        clazz.setId(902L);
        when(classRepository.findById(902L)).thenReturn(Optional.of(clazz));

        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(902L);
        when(classMemberRepository.existsMemberInClass(902L, 92L)).thenReturn(false);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.getListNotificationInClass(request, dto, Pageable.unpaged()));
        verify(classNotificationRepository, never()).findByClazzId(any(), anyString(), any());
    }
}
