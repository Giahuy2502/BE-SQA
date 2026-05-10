package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.EClassNotificationType;
import com.doan2025.webtoeic.constants.enums.ENotiType;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.AttachDocumentClass;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.domain.ClassNotification;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchNotificationInClassDto;
import com.doan2025.webtoeic.dto.request.ClassNotificationRequest;
import com.doan2025.webtoeic.dto.response.ClassNotificationResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
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
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassNotificationServiceImplTest {

    @Mock private ClassRepository classRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private ClassNotificationRepository classNotificationRepository;
    @Mock private AttachDocumentClassRepository attachDocumentClassRepository;
    @Mock private ClassMemberRepository classMemberRepository;
    @Mock private NotiUtils notiUtils;
    @Mock private HttpServletRequest request;
    @Mock private Pageable pageable;

    @InjectMocks
    private ClassNotificationServiceImpl classNotificationService;

    private User mockStudent;
    private User mockTeacher;
    private Class mockClass;
    private ClassNotification mockNotification;

    @BeforeEach
    void setUp() {
        mockStudent = new User();
        mockStudent.setId(1L);
        mockStudent.setEmail("student@gmail.com");
        mockStudent.setRole(ERole.STUDENT);

        mockTeacher = new User();
        mockTeacher.setId(2L);
        mockTeacher.setEmail("teacher@gmail.com");
        mockTeacher.setRole(ERole.TEACHER);

        mockClass = new Class();
        mockClass.setId(100L);
        mockClass.setTeacher(mockTeacher); // Setup liên kết quan trọng để không bị NPE

        mockNotification = new ClassNotification();
        mockNotification.setId(500L);
        mockNotification.setClazz(mockClass);
        mockNotification.setIsActive(true);
        mockNotification.setIsDelete(false);
    }

    // =========================================================================================
    // TESTS FOR getDetailNotificationInClass()
    // =========================================================================================

    @Test
    void getDetailNotificationInClass_UserNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn("student@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, 
                () -> classNotificationService.getDetailNotificationInClass(request, 500L));
    }

    @Test
    void getDetailNotificationInClass_NotificationNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail())).thenReturn(Optional.of(mockStudent));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, 
                () -> classNotificationService.getDetailNotificationInClass(request, 500L));
    }

    @Test
    void getDetailNotificationInClass_StudentNotInClass_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail())).thenReturn(Optional.of(mockStudent));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(false); // Not in class

        assertThrows(WebToeicException.class, 
                () -> classNotificationService.getDetailNotificationInClass(request, 500L));
    }

    @Test
    void getDetailNotificationInClass_StudentInClass_ReturnsResponse() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail())).thenReturn(Optional.of(mockStudent));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(true);
        when(convertUtil.convertClassNotificationToDto(any(), any(), any())).thenReturn(new ClassNotificationResponse());

        ClassNotificationResponse result = classNotificationService.getDetailNotificationInClass(request, 500L);
        
        assertNotNull(result);
        verify(attachDocumentClassRepository).findByClassNotificationId(500L);
    }

    @Test
    void getDetailNotificationInClass_Teacher_ReturnsResponseSkipsCheck() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));
        when(convertUtil.convertClassNotificationToDto(any(), any(), any())).thenReturn(new ClassNotificationResponse());

        ClassNotificationResponse result = classNotificationService.getDetailNotificationInClass(request, 500L);
        
        assertNotNull(result);
        verify(classMemberRepository, never()).existsMemberInClass(anyLong(), anyLong()); // Teacher skips check
    }

    // =========================================================================================
    // TESTS FOR getListNotificationInClass()
    // =========================================================================================

    @Test
    void getListNotificationInClass_ClassNotFound_ThrowsException() {
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail())).thenReturn(Optional.of(mockStudent));
        when(classRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, 
                () -> classNotificationService.getListNotificationInClass(request, dto, pageable));
    }

    @Test
    void getListNotificationInClass_StudentInClass_ReturnsPageResponse() {
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail())).thenReturn(Optional.of(mockStudent));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(true);

        Page<ClassNotification> mockPage = new PageImpl<>(List.of(mockNotification));
        when(classNotificationRepository.findByClazzId(dto, ERole.STUDENT.name(), pageable)).thenReturn(mockPage);
        when(convertUtil.convertClassNotificationToDto(any(), any(), any())).thenReturn(new ClassNotificationResponse());

        Page<ClassNotificationResponse> result = classNotificationService.getListNotificationInClass(request, dto, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getListNotificationInClass_Teacher_ReturnsPageSkipsCheck() {
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));

        Page<ClassNotification> mockPage = new PageImpl<>(List.of(mockNotification));
        when(classNotificationRepository.findByClazzId(dto, ERole.TEACHER.name(), pageable)).thenReturn(mockPage);

        when(convertUtil.convertClassNotificationToDto(any(), any(), any()))
        .thenReturn(new ClassNotificationResponse());

        Page<ClassNotificationResponse> result = classNotificationService.getListNotificationInClass(request, dto, pageable);

        assertNotNull(result);
        verify(classMemberRepository, never()).existsMemberInClass(anyLong(), anyLong());
    }
    @Test
    void getListNotificationInClass_StudentNotInClass_ThrowsException() {
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail()))
            .thenReturn(Optional.of(mockStudent));
        when(classRepository.findById(100L))
            .thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L))
            .thenReturn(false); // ← Chưa có test này

        assertThrows(WebToeicException.class,
            () -> classNotificationService.getListNotificationInClass(request, dto, pageable));
    }

    @Test
    void getListNotificationInClass_UserNotFound_ThrowsException() {
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn("unknown@gmail.com");
        when(userRepository.findByEmail("unknown@gmail.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
            () -> classNotificationService.getListNotificationInClass(request, dto, pageable));
    }

    // =========================================================================================
    // TESTS FOR createNotificationInClass()
    // =========================================================================================

    @Test
    void createNotificationInClass_UserNoPermission_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockStudent.getEmail());
        when(userRepository.findByEmail(mockStudent.getEmail())).thenReturn(Optional.of(mockStudent)); // Role STUDENT
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));

        assertThrows(WebToeicException.class, 
                () -> classNotificationService.createNotificationInClass(request, dtoRequest));
    }

    @Test
    void createNotificationInClass_TeacherWithPinNoAttachments_Success() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassId(100L);
        dtoRequest.setIsPin(true);
        dtoRequest.setUrlAttachment(null); // No attachments

        dtoRequest.setTypeNotification(1);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classNotificationRepository.save(any())).thenReturn(mockNotification);
        when(classMemberRepository.findMembersInClass(100L)).thenReturn(List.of(mockStudent));

        classNotificationService.createNotificationInClass(request, dtoRequest);

        verify(attachDocumentClassRepository, never()).save(any());
        verify(notiUtils, times(1)).sendNoti(anyList(), eq(ENotiType.UPDATE_IN_CLASS), anyString(), anyString(), eq(100L));
    }

    @Test
    void createNotificationInClass_ConsultantWithAttachments_Success() {
        User mockConsultant = new User();
        mockConsultant.setEmail("consultant@gmail.com");
        mockConsultant.setRole(ERole.CONSULTANT);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassId(100L);
        dtoRequest.setIsPin(null);
        dtoRequest.setUrlAttachment(List.of("url1", "url2", "url3", "url4")); // 4 attachments

        dtoRequest.setTypeNotification(2);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockConsultant.getEmail());
        when(userRepository.findByEmail(mockConsultant.getEmail())).thenReturn(Optional.of(mockConsultant));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classNotificationRepository.save(any())).thenReturn(mockNotification);
        when(classMemberRepository.findMembersInClass(100L))
        .thenReturn(List.of(mockStudent));

        classNotificationService.createNotificationInClass(request, dtoRequest);

        verify(attachDocumentClassRepository, times(4)).save(any(AttachDocumentClass.class));
        verify(notiUtils).sendNoti(anyList(), eq(ENotiType.UPDATE_IN_CLASS),
            anyString(), anyString(), eq(100L));
    }

    @Test
    void createNotificationInClass_ManagerRole_Success() {
        User mockManager = new User();
        mockManager.setEmail("manager@gmail.com");
        mockManager.setRole(ERole.MANAGER);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassId(100L);
        dtoRequest.setIsPin(false); // ← cover nhánh isPin != null && isPin = false
        dtoRequest.setUrlAttachment(new ArrayList<>()); // ← cover nhánh not null + empty
        dtoRequest.setTypeNotification(1);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockManager.getEmail());
        when(userRepository.findByEmail(mockManager.getEmail()))
            .thenReturn(Optional.of(mockManager));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classNotificationRepository.save(any())).thenReturn(mockNotification);
        when(classMemberRepository.findMembersInClass(100L)).thenReturn(List.of());

        classNotificationService.createNotificationInClass(request, dtoRequest);

        // isPin = false → saved noti.isPin = false
        verify(attachDocumentClassRepository, never()).save(any()); // empty list → no save
        verify(notiUtils).sendNoti(anyList(), eq(ENotiType.UPDATE_IN_CLASS), 
            anyString(), anyString(), eq(100L));
    }

    @Test
    void createNotificationInClass_UserNotFound_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassId(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn("ghost@gmail.com");
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
            () -> classNotificationService.createNotificationInClass(request, dtoRequest));
    }
    @Test
    void createNotificationInClass_ClassNotFound_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassId(999L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
            () -> classNotificationService.createNotificationInClass(request, dtoRequest));
    }

    // =========================================================================================
    // TESTS FOR updateNotificationInClass()
    // =========================================================================================

    @Test
    void updateNotificationInClass_UserNotTeacherOfClass_ThrowsException() {
        User wrongTeacher = new User();
        wrongTeacher.setEmail("wrong@gmail.com");
        wrongTeacher.setRole(ERole.TEACHER);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(wrongTeacher.getEmail());
        when(userRepository.findByEmail(wrongTeacher.getEmail())).thenReturn(Optional.of(wrongTeacher));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));

        assertThrows(WebToeicException.class, 
                () -> classNotificationService.updateNotificationInClass(request, dtoRequest));
    }

    @Test
    void updateNotificationInClass_WithNewAttachments_Success() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setUrlAttachment(List.of("url_new"));

        dtoRequest.setTypeNotification(1);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));

        classNotificationService.updateNotificationInClass(request, dtoRequest);

        verify(attachDocumentClassRepository).deleteAllAttachDocumentClassByClassNotificationId(500L);
        verify(attachDocumentClassRepository, times(1)).save(any(AttachDocumentClass.class));
    }
    @Test
    void updateNotificationInClass_EmptyAttachmentsAndFalsePin_Success() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setTypeNotification(1);
        
        // 1. Cover nhánh: isPin khác null (true) nhưng giá trị là false (false)
        dtoRequest.setIsPin(false); 
        
        // 2. Cover nhánh: URL không null (để vượt qua lệnh check if nonNull) nhưng List rỗng (vòng lặp for không chạy)
        dtoRequest.setUrlAttachment(new ArrayList<>()); 

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));

        classNotificationService.updateNotificationInClass(request, dtoRequest);

        // Vượt qua check Null nên sẽ chạy lệnh xoá, nhưng không có lệnh Save nào được gọi vì List rỗng
        verify(attachDocumentClassRepository).deleteAllAttachDocumentClassByClassNotificationId(500L);
        verify(attachDocumentClassRepository, never()).save(any(AttachDocumentClass.class));
    }
    @Test
    void updateNotificationInClass_NullPin_NoChangeToIsPin_Success() {
        // Arrange
        mockNotification.setIsPin(true); // đặt giá trị ban đầu rõ ràng
        
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setTypeNotification(1);
        dtoRequest.setIsPin(null);        // null → FieldUpdateUtil tính newValue = false
        dtoRequest.setUrlAttachment(null);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        // Act
        classNotificationService.updateNotificationInClass(request, dtoRequest);

        // Assert đúng mục tiêu:
        // isPin = (null != null && null) = false → FieldUpdateUtil sẽ update true→false
        assertFalse(mockNotification.getIsPin());
        assertEquals(mockTeacher, mockNotification.getUpdatedBy());
    }

    @Test
    void updateNotificationInClass_NotificationNotFound_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(999L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(999L))
            .thenReturn(Optional.empty()); // ← Not found

        assertThrows(WebToeicException.class,
            () -> classNotificationService.updateNotificationInClass(request, dtoRequest));
    }

    @Test
    void updateNotificationInClass_WithDateRange_Success() {
        Date from = new Date(1000000L);
        Date to   = new Date(2000000L);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setTypeNotification(1);
        dtoRequest.setIsPin(null);
        dtoRequest.setUrlAttachment(null);
        dtoRequest.setFromDate(from);
        dtoRequest.setToDate(to);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        classNotificationService.updateNotificationInClass(request, dtoRequest);

        // Assert đúng mục tiêu — verify fromDate và toDate thực sự được set
        assertEquals(from, mockNotification.getFromDate());
        assertEquals(to,   mockNotification.getToDate());
        assertEquals(mockTeacher, mockNotification.getUpdatedBy());
    }

    @Test
    void updateNotificationInClass_UserNotFound_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn("ghost@gmail.com");
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
            () -> classNotificationService.updateNotificationInClass(request, dtoRequest));
    }
    
    @Test
    void updateNotificationInClass_ConsultantRole_BugNotPermission_ShouldSucceedAfterFix() {
        User mockConsultant = new User();
        mockConsultant.setId(3L);
        mockConsultant.setEmail("consultant@gmail.com");
        mockConsultant.setRole(ERole.CONSULTANT);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setTypeNotification(1);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockConsultant.getEmail());
        when(userRepository.findByEmail(mockConsultant.getEmail()))
                .thenReturn(Optional.of(mockConsultant));
        when(classNotificationRepository.findById(500L))
                .thenReturn(Optional.of(mockNotification));
        when(convertUtil.convertClassNotificationToDto(any(), any(), any()))
                .thenReturn(new ClassNotificationResponse());

        // FAIL cho đến khi BE fix permission check
        assertDoesNotThrow(
                () -> classNotificationService.updateNotificationInClass(request, dtoRequest),
                "[BUG] Consultant tạo được nhưng không update được — vi phạm đặc tả"
        );
    }

    @Test
    void updateNotificationInClass_ManagerRole_BugNotPermission_ShouldSucceedAfterFix() {
        User mockManager = new User();
        mockManager.setId(4L);
        mockManager.setEmail("manager@gmail.com");
        mockManager.setRole(ERole.MANAGER);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setTypeNotification(1);
        dtoRequest.setIsPin(false);
        dtoRequest.setUrlAttachment(new ArrayList<>());

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockManager.getEmail());
        when(userRepository.findByEmail(mockManager.getEmail()))
                .thenReturn(Optional.of(mockManager));
        when(classNotificationRepository.findById(500L))
                .thenReturn(Optional.of(mockNotification));
        when(convertUtil.convertClassNotificationToDto(any(), any(), any()))
                .thenReturn(new ClassNotificationResponse());

        // FAIL cho đến khi BE fix permission check
        assertDoesNotThrow(
                () -> classNotificationService.updateNotificationInClass(request, dtoRequest),
                "[BUG] Manager tạo được nhưng không update được — vi phạm đặc tả"
        );
    }

    @Test
    void updateNotificationInClass_IsPinTrue_SetsPinTrue() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setTypeNotification(1);
        dtoRequest.setIsPin(true);      // ← cover nhánh isPin != null && isPin = true
        dtoRequest.setUrlAttachment(null);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        classNotificationService.updateNotificationInClass(request, dtoRequest);

        assertTrue(mockNotification.getIsPin()); // ← isPin được set thành true
    }
    // =========================================================================================
    // TESTS FOR disableOrDeleteNotificationInClass()
    // =========================================================================================

    @Test
    void disableOrDeleteNotificationInClass_UpdateBothFlags_Success() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setIsActive(false); // Change from true -> false
        dtoRequest.setIsDelete(true);  // Change from false -> true

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail())).thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L)).thenReturn(Optional.of(mockNotification));

        classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest);

        assertFalse(mockNotification.getIsActive());
        assertTrue(mockNotification.getIsDelete());
        assertEquals(mockTeacher, mockNotification.getUpdatedBy());
    }

    @Test
    void disableOrDeleteNotificationInClass_NotificationNotFound_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(999L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
            () -> classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest));
    }

    @Test
    void disableOrDeleteNotificationInClass_WrongTeacher_ThrowsException() {
        User wrongUser = new User();
        wrongUser.setEmail("wrong@gmail.com");
        wrongUser.setRole(ERole.TEACHER);

        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(wrongUser.getEmail());
        when(userRepository.findByEmail(wrongUser.getEmail()))
            .thenReturn(Optional.of(wrongUser));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        assertThrows(WebToeicException.class,
            () -> classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest));
    }   
    @Test
    void disableOrDeleteNotificationInClass_OnlyIsActiveChanged_IsDeleteNull() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setIsActive(false); // ← thay đổi
        dtoRequest.setIsDelete(null);  // ← null, không đổi

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest);

        assertFalse(mockNotification.getIsActive()); // ← đã đổi
        assertFalse(mockNotification.getIsDelete()); // ← vẫn giữ nguyên false
    }

    @Test
    void disableOrDeleteNotificationInClass_IsActiveNullOnlyIsDeleteChanged() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setIsActive(null);  // ← null
        dtoRequest.setIsDelete(true);  // ← thay đổi

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest);

        assertTrue(mockNotification.getIsActive());  // ← vẫn giữ nguyên true
        assertTrue(mockNotification.getIsDelete());  // ← đã đổi
    }
    @Test
    void disableOrDeleteNotificationInClass_UserNotFound_ThrowsException() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn("ghost@gmail.com");
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
            () -> classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest));
    }
    
    @Test
    void disableOrDeleteNotificationInClass_FlagsNull_NoChange_ReturnsResponse() {
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setIsActive(null);
        dtoRequest.setIsDelete(null);

        ClassNotificationResponse expectedResponse = new ClassNotificationResponse();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
                .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
                .thenReturn(Optional.of(mockNotification));
        when(convertUtil.convertClassNotificationToDto(any(), any(), any()))
                .thenReturn(expectedResponse);

        ClassNotificationResponse result =
                classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest);

        assertNotNull(result);
        assertEquals(expectedResponse, result);       // từ VerifyReturnValue
        assertTrue(mockNotification.getIsActive());   // từ FlagsNull_NoChange
        assertFalse(mockNotification.getIsDelete());
    }
    @Test
    void getListNotificationInClass_ConsultantRole_SkipsClassMemberCheck() {
        User mockConsultant = new User();
        mockConsultant.setId(3L); 
        mockConsultant.setEmail("consultant@gmail.com");
        mockConsultant.setRole(ERole.CONSULTANT);
 
        SearchNotificationInClassDto dto = new SearchNotificationInClassDto();
        dto.setClassId(100L);
 
        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockConsultant.getEmail());
        when(userRepository.findByEmail(mockConsultant.getEmail()))
                .thenReturn(Optional.of(mockConsultant));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
 
        Page<ClassNotification> mockPage = new PageImpl<>(List.of(mockNotification));
        when(classNotificationRepository.findByClazzId(dto, ERole.CONSULTANT.name(), pageable))
                .thenReturn(mockPage);
        when(convertUtil.convertClassNotificationToDto(any(), any(), any()))
                .thenReturn(new ClassNotificationResponse());
 
        Page<ClassNotificationResponse> result =
                classNotificationService.getListNotificationInClass(request, dto, pageable);
 
        assertNotNull(result);
        verify(classMemberRepository, never()).existsMemberInClass(anyLong(), anyLong());
    }
    @Test
    void disableOrDeleteNotificationInClass_IsActiveAlreadySameValue_NoChange() {
        // mockNotification.isActive = true (setup sẵn trong @BeforeEach)
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setIsActive(true);  // ← bằng đúng giá trị hiện tại → Objects.equals = true → không set
        dtoRequest.setIsDelete(null);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest);

        assertTrue(mockNotification.getIsActive()); // không đổi
    }

    @Test
    void disableOrDeleteNotificationInClass_IsDeleteAlreadySameValue_NoChange() {
        // mockNotification.isDelete = false (setup sẵn trong @BeforeEach)
        ClassNotificationRequest dtoRequest = new ClassNotificationRequest();
        dtoRequest.setClassNotificationId(500L);
        dtoRequest.setIsActive(null);
        dtoRequest.setIsDelete(false); // ← bằng đúng giá trị hiện tại → Objects.equals = true → không set

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockTeacher.getEmail());
        when(userRepository.findByEmail(mockTeacher.getEmail()))
            .thenReturn(Optional.of(mockTeacher));
        when(classNotificationRepository.findById(500L))
            .thenReturn(Optional.of(mockNotification));

        classNotificationService.disableOrDeleteNotificationInClass(request, dtoRequest);

        assertFalse(mockNotification.getIsDelete()); // không đổi
    }
}