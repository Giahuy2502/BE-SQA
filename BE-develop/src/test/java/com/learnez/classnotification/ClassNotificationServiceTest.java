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

    // TC-CLSNOTI-011: getList for student member should return mapped page.
    @Test
    public void TC_CLSNOTI_011_getList_studentMember_shouldReturnMappedPage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "student-list@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User student = new User();
        student.setId(22L);
        student.setEmail(email);
        student.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(student));

        Class clazz = new Class();
        clazz.setId(202L);
        when(classRepository.findById(202L)).thenReturn(Optional.of(clazz));
        when(classMemberRepository.existsMemberInClass(202L, 22L)).thenReturn(true);

        ClassNotification notification = new ClassNotification();
        notification.setId(2002L);
        notification.setClazz(clazz);
        Page<ClassNotification> page = new PageImpl<>(List.of(notification));

        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(202L);
        when(classNotificationRepository.findByClazzId(eq(dto), eq("STUDENT"), any(Pageable.class))).thenReturn(page);
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(2002L).build());

        Page<ClassNotificationResponse> result = classNotificationService.getListNotificationInClass(request, dto,
                Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        verify(classMemberRepository).existsMemberInClass(202L, 22L);
        verify(classNotificationRepository).findByClazzId(eq(dto), eq("STUDENT"), any(Pageable.class));
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

    // TC-CLSNOTI-013: createNotification without attachments should still create
    // notification and send noti, but must not persist attachments.
    @Test
    public void TC_CLSNOTI_013_createNotification_withoutAttachments_shouldSkipAttachmentSave() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-no-attach@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(33L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(303L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(303L)).thenReturn(Optional.of(clazz));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(303L);
        req.setDescription("No attachment notification");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());
        req.setUrlAttachment(null);

        doAnswer(invocation -> invocation.getArgument(0)).when(classNotificationRepository)
                .save(any(ClassNotification.class));
        when(classMemberRepository.findMembersInClass(303L)).thenReturn(List.of(teacher));
        when(convertUtil.convertClassNotificationToDto(eq(request), any(ClassNotification.class), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(3030L).build());

        ClassNotificationResponse result = classNotificationService.createNotificationInClass(request, req);

        assertNotNull(result);
        verify(classNotificationRepository).save(any(ClassNotification.class));
        verify(attachDocumentClassRepository, never()).save(any(AttachDocumentClass.class));
        verify(notiUtils).sendNoti(eq(List.of(teacher)), any(), anyString(), anyString(), eq(303L));
        // Rollback: mock repository used; no DB changes in real DB.
    }

    @Test
    public void TC_CLSNOTI_005_createNotification_unauthorizedUser_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String userEmail = "student@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(userEmail);

        User user = new User();
        user.setId(41L);
        user.setEmail(userEmail);
        user.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        User teacher = new User();
        teacher.setId(42L);
        teacher.setEmail("teacher@class.com");
        teacher.setRole(ERole.TEACHER);

        Class clazz = new Class();
        clazz.setId(401L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(401L)).thenReturn(Optional.of(clazz));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(401L);
        req.setDescription("Forbidden create");
        req.setTypeNotification(EClassNotificationType.EXERCISE.getValue());

        assertThrows(WebToeicException.class,
                () -> classNotificationService.createNotificationInClass(request, req));
        verify(classNotificationRepository, never()).save(any());
        verify(notiUtils, never()).sendNoti(anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    public void TC_CLSNOTI_006_updateNotification_teacher_shouldUpdateAttachments() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher2@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(51L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(501L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(5001L);
        notification.setClazz(clazz);
        notification.setDescription("Old description");
        notification.setTypeNotification(EClassNotificationType.NOTIFICATION);
        when(classNotificationRepository.findById(5001L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(5001L);
        req.setDescription("New description");
        req.setTypeNotification(EClassNotificationType.EXERCISE.getValue());
        req.setIsPin(false);
        req.setUrlAttachment(List.of("https://example.com/new.pdf"));

        AttachDocumentClass attachment = new AttachDocumentClass();
        attachment.setId(9001L);
        when(attachDocumentClassRepository.findByClassNotificationId(5001L)).thenReturn(List.of(attachment));

        ClassNotificationResponse expected = ClassNotificationResponse.builder().id(5001L)
                .description("New description").build();
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList())).thenReturn(expected);

        ClassNotificationResponse result = classNotificationService.updateNotificationInClass(request, req);

        assertEquals(expected, result);
        assertEquals("New description", notification.getDescription());
        assertEquals(EClassNotificationType.EXERCISE, notification.getTypeNotification());
        assertEquals(teacherEmail, notification.getUpdatedBy().getEmail());
        verify(attachDocumentClassRepository).deleteAllAttachDocumentClassByClassNotificationId(5001L);
        verify(attachDocumentClassRepository).save(any(AttachDocumentClass.class));
    }

    // TC-CLSNOTI-014: updateNotification without attachments should keep existing
    // attachments untouched and still update the notification content.
    @Test
    public void TC_CLSNOTI_014_updateNotification_withoutAttachments_shouldKeepAttachments() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-no-attach-update@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(52L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(502L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(5002L);
        notification.setClazz(clazz);
        notification.setDescription("Old description");
        notification.setTypeNotification(EClassNotificationType.NOTIFICATION);
        when(classNotificationRepository.findById(5002L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(5002L);
        req.setDescription("Updated description");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());
        req.setUrlAttachment(null);

        when(attachDocumentClassRepository.findByClassNotificationId(5002L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(5002L).description("Updated description").build());

        ClassNotificationResponse result = classNotificationService.updateNotificationInClass(request, req);

        assertEquals("Updated description", result.getDescription());
        verify(attachDocumentClassRepository, never()).deleteAllAttachDocumentClassByClassNotificationId(anyLong());
        verify(attachDocumentClassRepository, never()).save(any(AttachDocumentClass.class));
    }

    @Test
    public void TC_CLSNOTI_007_updateNotification_unauthorizedUser_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String userEmail = "student2@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(userEmail);

        User user = new User();
        user.setId(61L);
        user.setEmail(userEmail);
        user.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        User teacher = new User();
        teacher.setId(62L);
        teacher.setEmail("teacher3@class.com");
        teacher.setRole(ERole.TEACHER);

        Class clazz = new Class();
        clazz.setId(601L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(6001L);
        notification.setClazz(clazz);
        when(classNotificationRepository.findById(6001L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(6001L);
        req.setDescription("Attempt update");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());

        assertThrows(WebToeicException.class,
                () -> classNotificationService.updateNotificationInClass(request, req));
        verify(convertUtil, never()).convertClassNotificationToDto(any(), any(), anyList());
    }

    @Test
    public void TC_CLSNOTI_008_disableNotification_teacher_shouldToggleStatus() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher4@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(71L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(701L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(7001L);
        notification.setClazz(clazz);
        notification.setIsActive(false);
        notification.setIsDelete(false);
        when(classNotificationRepository.findById(7001L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(7001L);
        req.setIsActive(true);
        req.setIsDelete(true);

        when(attachDocumentClassRepository.findByClassNotificationId(7001L)).thenReturn(List.of());
        ClassNotificationResponse expected = ClassNotificationResponse.builder().id(7001L).isActive(true).isDelete(true)
                .build();
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList())).thenReturn(expected);

        ClassNotificationResponse result = classNotificationService.disableOrDeleteNotificationInClass(request, req);

        assertEquals(expected, result);
        assertTrue(Boolean.TRUE.equals(notification.getIsActive()));
        assertTrue(Boolean.TRUE.equals(notification.getIsDelete()));
        assertEquals(teacherEmail, notification.getUpdatedBy().getEmail());
    }

    // TC-CLSNOTI-015: disable/delete notification with no status change should
    // preserve current values.
    @Test
    public void TC_CLSNOTI_015_disableNotification_withoutChange_shouldPreserveStatus() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-no-change@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(72L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(702L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(7002L);
        notification.setClazz(clazz);
        notification.setIsActive(false);
        notification.setIsDelete(false);
        when(classNotificationRepository.findById(7002L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(7002L);
        req.setIsActive(false);
        req.setIsDelete(false);

        when(attachDocumentClassRepository.findByClassNotificationId(7002L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(7002L).isActive(false).isDelete(false).build());

        ClassNotificationResponse result = classNotificationService.disableOrDeleteNotificationInClass(request, req);

        assertEquals(false, result.getIsActive());
        assertEquals(false, result.getIsDelete());
        verify(attachDocumentClassRepository, never()).deleteAllAttachDocumentClassByClassNotificationId(anyLong());
    }

    @Test
    public void TC_CLSNOTI_009_disableNotification_unauthorizedUser_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String userEmail = "student3@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(userEmail);

        User user = new User();
        user.setId(81L);
        user.setEmail(userEmail);
        user.setRole(ERole.STUDENT);
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        User teacher = new User();
        teacher.setId(82L);
        teacher.setEmail("teacher5@class.com");
        teacher.setRole(ERole.TEACHER);

        Class clazz = new Class();
        clazz.setId(801L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(8001L);
        notification.setClazz(clazz);
        when(classNotificationRepository.findById(8001L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(8001L);
        req.setIsActive(true);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.disableOrDeleteNotificationInClass(request, req));
        verify(convertUtil, never()).convertClassNotificationToDto(any(), any(), anyList());
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

    // TC-CLSNOTI-018: createNotification with missing user should throw
    // NOT_EXISTED.
    @Test
    public void TC_CLSNOTI_018_createNotification_userNotFound_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtil.getEmailFromToken(request)).thenReturn("missing-create-user@class.com");
        when(userRepository.findByEmail("missing-create-user@class.com")).thenReturn(Optional.empty());

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(903L);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.createNotificationInClass(request, req));
        verify(classRepository, never()).findById(anyLong());
    }

    // TC-CLSNOTI-019: createNotification with missing class should throw
    // NOT_EXISTED.
    @Test
    public void TC_CLSNOTI_019_createNotification_classNotFound_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "consultant-missing-class@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User consultant = new User();
        consultant.setId(93L);
        consultant.setEmail(email);
        consultant.setRole(ERole.CONSULTANT);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(consultant));
        when(classRepository.findById(99999L)).thenReturn(Optional.empty());

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(99999L);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.createNotificationInClass(request, req));
        verify(classNotificationRepository, never()).save(any(ClassNotification.class));
    }

    // TC-CLSNOTI-020: updateNotification with missing notification should throw
    // NOT_EXISTED.
    @Test
    public void TC_CLSNOTI_020_updateNotification_notificationNotFound_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "teacher-missing-noti-update@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User teacher = new User();
        teacher.setId(94L);
        teacher.setEmail(email);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(teacher));
        when(classNotificationRepository.findById(123456L)).thenReturn(Optional.empty());

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(123456L);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.updateNotificationInClass(request, req));
        verify(attachDocumentClassRepository, never()).deleteAllAttachDocumentClassByClassNotificationId(anyLong());
    }

    // TC-CLSNOTI-021: disable/delete with missing notification should throw
    // NOT_EXISTED.
    @Test
    public void TC_CLSNOTI_021_disableNotification_notificationNotFound_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "teacher-missing-noti-disable@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User teacher = new User();
        teacher.setId(95L);
        teacher.setEmail(email);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(teacher));
        when(classNotificationRepository.findById(654321L)).thenReturn(Optional.empty());

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(654321L);

        assertThrows(WebToeicException.class,
                () -> classNotificationService.disableOrDeleteNotificationInClass(request, req));
        verify(convertUtil, never()).convertClassNotificationToDto(any(), any(), anyList());
    }

    // TC-CLSNOTI-022: createNotification with consultant role (NOT teacher and NOT
    // manager) should succeed
    @Test
    public void TC_CLSNOTI_022_createNotification_consultantRole_shouldSucceed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "consultant-create@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User consultant = new User();
        consultant.setId(96L);
        consultant.setEmail(email);
        consultant.setRole(ERole.CONSULTANT);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(consultant));

        User teacher = new User();
        teacher.setId(97L);
        teacher.setEmail("teacher-other@class.com");
        teacher.setRole(ERole.TEACHER);

        Class clazz = new Class();
        clazz.setId(905L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(905L)).thenReturn(Optional.of(clazz));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(905L);
        req.setDescription("Consultant notification");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());

        doAnswer(invocation -> {
            ClassNotification notification = invocation.getArgument(0);
            notification.setId(9005L);
            notification.setIsActive(true);
            notification.setIsDelete(false);
            return notification;
        }).when(classNotificationRepository).save(any(ClassNotification.class));

        when(attachDocumentClassRepository.findByClassNotificationId(9005L)).thenReturn(List.of());
        when(classMemberRepository.findMembersInClass(905L)).thenReturn(List.of(consultant));
        when(convertUtil.convertClassNotificationToDto(eq(request), any(ClassNotification.class), anyList()))
                .thenReturn(
                        ClassNotificationResponse.builder().id(9005L).description("Consultant notification").build());

        ClassNotificationResponse result = classNotificationService.createNotificationInClass(request, req);

        assertNotNull(result);
        verify(classNotificationRepository).save(any(ClassNotification.class));
        verify(notiUtils).sendNoti(eq(List.of(consultant)), any(), anyString(), anyString(), eq(905L));
    }

    // TC-CLSNOTI-023: createNotification with manager role (NOT teacher and NOT
    // consultant) should succeed
    @Test
    public void TC_CLSNOTI_023_createNotification_managerRole_shouldSucceed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "manager-create@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User manager = new User();
        manager.setId(98L);
        manager.setEmail(email);
        manager.setRole(ERole.MANAGER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(manager));

        User teacher = new User();
        teacher.setId(99L);
        teacher.setEmail("teacher-other2@class.com");
        teacher.setRole(ERole.TEACHER);

        Class clazz = new Class();
        clazz.setId(906L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(906L)).thenReturn(Optional.of(clazz));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(906L);
        req.setDescription("Manager notification");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());

        doAnswer(invocation -> {
            ClassNotification notification = invocation.getArgument(0);
            notification.setId(9006L);
            notification.setIsActive(true);
            notification.setIsDelete(false);
            return notification;
        }).when(classNotificationRepository).save(any(ClassNotification.class));

        when(attachDocumentClassRepository.findByClassNotificationId(9006L)).thenReturn(List.of());
        when(classMemberRepository.findMembersInClass(906L)).thenReturn(List.of(manager));
        when(convertUtil.convertClassNotificationToDto(eq(request), any(ClassNotification.class), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9006L).description("Manager notification").build());

        ClassNotificationResponse result = classNotificationService.createNotificationInClass(request, req);

        assertNotNull(result);
        verify(classNotificationRepository).save(any(ClassNotification.class));
        verify(notiUtils).sendNoti(eq(List.of(manager)), any(), anyString(), anyString(), eq(906L));
    }

    // TC-CLSNOTI-024: createNotification with empty attachments list should not
    // save any attachment
    @Test
    public void TC_CLSNOTI_024_createNotification_emptyAttachmentList_shouldNotSaveAttachment() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "teacher-empty-attach@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User teacher = new User();
        teacher.setId(100L);
        teacher.setEmail(email);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(907L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(907L)).thenReturn(Optional.of(clazz));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(907L);
        req.setDescription("Empty attachments");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());
        req.setUrlAttachment(List.of());

        doAnswer(invocation -> {
            ClassNotification notification = invocation.getArgument(0);
            notification.setId(9007L);
            notification.setIsActive(true);
            notification.setIsDelete(false);
            return notification;
        }).when(classNotificationRepository).save(any(ClassNotification.class));

        when(attachDocumentClassRepository.findByClassNotificationId(9007L)).thenReturn(List.of());
        when(classMemberRepository.findMembersInClass(907L)).thenReturn(List.of(teacher));
        when(convertUtil.convertClassNotificationToDto(eq(request), any(ClassNotification.class), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9007L).description("Empty attachments").build());

        ClassNotificationResponse result = classNotificationService.createNotificationInClass(request, req);

        assertNotNull(result);
        verify(attachDocumentClassRepository, never()).save(any(AttachDocumentClass.class));
    }

    // TC-CLSNOTI-025: updateNotification with empty attachments list should not
    // save any attachment
    @Test
    public void TC_CLSNOTI_025_updateNotification_emptyAttachmentList_shouldNotSaveAttachment() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-update-empty-attach@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(101L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(908L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(9008L);
        notification.setClazz(clazz);
        notification.setDescription("Old description");
        notification.setTypeNotification(EClassNotificationType.NOTIFICATION);
        when(classNotificationRepository.findById(9008L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(9008L);
        req.setDescription("Updated description");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());
        req.setUrlAttachment(List.of());

        when(attachDocumentClassRepository.findByClassNotificationId(9008L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9008L).description("Updated description").build());

        ClassNotificationResponse result = classNotificationService.updateNotificationInClass(request, req);

        assertNotNull(result);
        verify(attachDocumentClassRepository).deleteAllAttachDocumentClassByClassNotificationId(9008L);
        verify(attachDocumentClassRepository, never()).save(any(AttachDocumentClass.class));
    }

    // TC-CLSNOTI-026: disableNotification with isActive not changed should not
    // update isActive
    @Test
    public void TC_CLSNOTI_026_disableNotification_isActiveUnchanged_shouldNotModify() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-disable-unchanged@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(102L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        User createdBy = new User();
        createdBy.setId(103L);
        createdBy.setEmail("creator@class.com");

        Class clazz = new Class();
        clazz.setId(909L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(9009L);
        notification.setClazz(clazz);
        notification.setIsActive(true);
        notification.setIsDelete(false);
        notification.setCreatedBy(createdBy);
        when(classNotificationRepository.findById(9009L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(9009L);
        req.setIsActive(true);
        req.setIsDelete(null);

        when(attachDocumentClassRepository.findByClassNotificationId(9009L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9009L).isActive(true).build());

        ClassNotificationResponse result = classNotificationService.disableOrDeleteNotificationInClass(request, req);

        assertNotNull(result);
        assertTrue(notification.getIsActive());
        assertEquals(teacher, notification.getUpdatedBy());
    }

    // TC-CLSNOTI-027: disableNotification with isDelete changed should update
    // isDelete
    @Test
    public void TC_CLSNOTI_027_disableNotification_isDeleteChanged_shouldUpdate() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-delete-changed@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(104L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        User createdBy = new User();
        createdBy.setId(105L);
        createdBy.setEmail("creator2@class.com");

        Class clazz = new Class();
        clazz.setId(910L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(9010L);
        notification.setClazz(clazz);
        notification.setIsActive(true);
        notification.setIsDelete(false);
        notification.setCreatedBy(createdBy);
        when(classNotificationRepository.findById(9010L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(9010L);
        req.setIsActive(null);
        req.setIsDelete(true);

        when(attachDocumentClassRepository.findByClassNotificationId(9010L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9010L).isDelete(true).build());

        ClassNotificationResponse result = classNotificationService.disableOrDeleteNotificationInClass(request, req);

        assertNotNull(result);
        assertTrue(notification.getIsDelete());
        assertEquals(teacher, notification.getUpdatedBy());
    }

    // TC-CLSNOTI-028: createNotification with multiple attachments should save all
    // attachments
    @Test
    public void TC_CLSNOTI_028_createNotification_withMultipleAttachments_shouldSaveAllAttachments() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String email = "teacher-multi-attach@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(email);

        User teacher = new User();
        teacher.setId(106L);
        teacher.setEmail(email);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(911L);
        clazz.setTeacher(teacher);
        when(classRepository.findById(911L)).thenReturn(Optional.of(clazz));

        List<String> urls = List.of("https://example.com/1.pdf", "https://example.com/2.pdf",
                "https://example.com/3.pdf");
        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassId(911L);
        req.setDescription("With attachments");
        req.setTypeNotification(EClassNotificationType.NOTIFICATION.getValue());
        req.setUrlAttachment(urls);

        doAnswer(invocation -> {
            ClassNotification notification = invocation.getArgument(0);
            notification.setId(9011L);
            notification.setIsActive(true);
            notification.setIsDelete(false);
            return notification;
        }).when(classNotificationRepository).save(any(ClassNotification.class));

        when(attachDocumentClassRepository.findByClassNotificationId(9011L)).thenReturn(List.of());
        when(classMemberRepository.findMembersInClass(911L)).thenReturn(List.of(teacher));
        when(convertUtil.convertClassNotificationToDto(eq(request), any(ClassNotification.class), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9011L).description("With attachments").build());

        ClassNotificationResponse result = classNotificationService.createNotificationInClass(request, req);

        assertNotNull(result);
        verify(classNotificationRepository).save(any(ClassNotification.class));
        verify(attachDocumentClassRepository, times(3)).save(any(AttachDocumentClass.class));
        verify(notiUtils).sendNoti(eq(List.of(teacher)), any(), anyString(), anyString(), eq(911L));
    }

    // TC-CLSNOTI-029: updateNotification with non-null attachment should delete old
    // and save new
    @Test
    public void TC_CLSNOTI_029_updateNotification_withAttachment_shouldDeleteAndSaveNew() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String teacherEmail = "teacher-update-new-attach@class.com";
        when(jwtUtil.getEmailFromToken(request)).thenReturn(teacherEmail);

        User teacher = new User();
        teacher.setId(108L);
        teacher.setEmail(teacherEmail);
        teacher.setRole(ERole.TEACHER);
        when(userRepository.findByEmail(teacherEmail)).thenReturn(Optional.of(teacher));

        Class clazz = new Class();
        clazz.setId(912L);
        clazz.setTeacher(teacher);

        ClassNotification notification = new ClassNotification();
        notification.setId(9012L);
        notification.setClazz(clazz);
        notification.setDescription("Old");
        when(classNotificationRepository.findById(9012L)).thenReturn(Optional.of(notification));

        ClassNotificationRequest req = new ClassNotificationRequest();
        req.setClassNotificationId(9012L);
        req.setDescription("New with attach");
        req.setTypeNotification(EClassNotificationType.EXERCISE.getValue());
        req.setUrlAttachment(List.of("https://example.com/new1.pdf", "https://example.com/new2.pdf"));

        when(attachDocumentClassRepository.findByClassNotificationId(9012L)).thenReturn(List.of());
        when(convertUtil.convertClassNotificationToDto(eq(request), eq(notification), anyList()))
                .thenReturn(ClassNotificationResponse.builder().id(9012L).description("New with attach").build());

        ClassNotificationResponse result = classNotificationService.updateNotificationInClass(request, req);

        assertNotNull(result);
        verify(attachDocumentClassRepository).deleteAllAttachDocumentClassByClassNotificationId(9012L);
        verify(attachDocumentClassRepository, times(2)).save(any(AttachDocumentClass.class));
        assertEquals("New with attach", result.getDescription());
    }
}
