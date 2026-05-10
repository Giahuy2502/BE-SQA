package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.enums.*;
import com.doan2025.webtoeic.domain.Class;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    @Mock private ClassRepository classRepository;
    @Mock private ClassMemberRepository classMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ConvertUtil convertUtil;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private ClassServiceImpl classService;

    private User mockUser;
    private Class mockClass;
    private final String EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail(EMAIL);
        mockUser.setCode("U001");

        mockClass = new Class();
        mockClass.setId(100L);
        mockClass.setTeacher(mockUser);
    }

    // ============================================================
    // get()
    // ============================================================

    @Test
    void get_UserNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, () -> classService.get(request, 100L));
    }

    @Test
    void get_ClassNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, () -> classService.get(request, 100L));
    }

    @Test
    void get_StudentNotMember_ThrowsException() {
        // Branch: !existsMember=true AND role=STUDENT → throw
        mockUser.setRole(ERole.STUDENT);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(false);

        assertThrows(WebToeicException.class, () -> classService.get(request, 100L));
    }

    @Test
    void get_StudentIsMember_ReturnsClassResponse() {
        // Branch: !existsMember=false → condition false → return (không throw)
        mockUser.setRole(ERole.STUDENT);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(true);
        when(convertUtil.convertClassToDto(request, mockClass)).thenReturn(new ClassResponse());

        ClassResponse response = classService.get(request, 100L);
        assertNotNull(response);
    }

    @Test
    void get_ManagerRole_ReturnsClassResponse() {
        // Branch: role != STUDENT → second condition false → short-circuit AND → không throw
        mockUser.setRole(ERole.MANAGER);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(true);
        when(convertUtil.convertClassToDto(request, mockClass)).thenReturn(new ClassResponse());

        ClassResponse response = classService.get(request, 100L);
        assertNotNull(response);
    }

    @Test
    void get_TeacherRole_ReturnsClassResponse() {
        // Branch: role=TEACHER → role != STUDENT → không throw
        mockUser.setRole(ERole.TEACHER);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(true);
        when(convertUtil.convertClassToDto(request, mockClass)).thenReturn(new ClassResponse());

        ClassResponse response = classService.get(request, 100L);
        assertNotNull(response);
    }

    @Test
    void get_NonStudentNotMember_ReturnsClassResponse() {
        // Branch: !existsMember=true AND role!=STUDENT → condition false → không throw
        mockUser.setRole(ERole.MANAGER);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classMemberRepository.existsMemberInClass(100L, 1L)).thenReturn(false); // không phải member
        when(convertUtil.convertClassToDto(request, mockClass)).thenReturn(new ClassResponse());

        ClassResponse response = classService.get(request, 100L);
        assertNotNull(response);
    }
    // ============================================================
    // getClasses()
    // ============================================================

    @Test
    void getClasses_UserNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.getClasses(request, new SearchClassDto(), PageRequest.of(0, 10)));
    }

    @Test
    void getClasses_StudentRole_FiltersByMemberIds() {
        // Branch: role=STUDENT → first OR true → dùng memberIds
        mockUser.setRole(ERole.STUDENT);
        SearchClassDto dto = new SearchClassDto();
        Pageable pageable = PageRequest.of(0, 10);
        List<Long> classIds = List.of(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classMemberRepository.findClassOfMember(EMAIL)).thenReturn(classIds);
        when(classRepository.filterClass(dto, classIds, pageable))
                .thenReturn(new PageImpl<>(List.of(mockClass)));

        Page<ClassResponse> result = classService.getClasses(request, dto, pageable);
        assertNotNull(result);
        verify(classRepository).filterClass(eq(dto), eq(classIds), any());
    }

    @Test
    void getClasses_TeacherRole_FiltersByMemberIds() {
        // Branch: role=STUDENT=false, role=TEACHER=true → first condition true → dùng memberIds
        mockUser.setRole(ERole.TEACHER);
        SearchClassDto dto = new SearchClassDto();
        Pageable pageable = PageRequest.of(0, 10);
        List<Long> classIds = List.of(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classMemberRepository.findClassOfMember(EMAIL)).thenReturn(classIds);
        when(classRepository.filterClass(dto, classIds, pageable))
                .thenReturn(new PageImpl<>(List.of(mockClass)));

        Page<ClassResponse> result = classService.getClasses(request, dto, pageable);
        assertNotNull(result);
        verify(classRepository).filterClass(eq(dto), eq(classIds), any());
    }

    @Test
    void getClasses_ConsultantRole_FiltersWithNullIds() {
        // Branch: else if role=CONSULTANT → null ids
        mockUser.setRole(ERole.CONSULTANT);
        SearchClassDto dto = new SearchClassDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.filterClass(dto, null, pageable))
                .thenReturn(new PageImpl<>(List.of(mockClass)));

        classService.getClasses(request, dto, pageable);
        verify(classRepository).filterClass(dto, null, pageable);
    }

    @Test
    void getClasses_ManagerRole_FiltersWithNullIds() {
        // Branch: role=CONSULTANT=false, role=MANAGER=true → null ids
        mockUser.setRole(ERole.MANAGER);
        SearchClassDto dto = new SearchClassDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.filterClass(dto, null, pageable))
                .thenReturn(new PageImpl<>(List.of(mockClass)));

        classService.getClasses(request, dto, pageable);
        verify(classRepository).filterClass(dto, null, pageable);
    }
    @Test
    void getClasses_UnknownRole_ReturnsEmptyPage() {
        // Branch: cả 2 if/else-if đều false → classes = Page.empty() → map → empty result
        mockUser.setRole(null); // null role → không match bất kỳ ERole nào
        SearchClassDto dto = new SearchClassDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));

        Page<ClassResponse> result = classService.getClasses(request, dto, pageable);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getClasses_SearchStringWithUntrimmedWhitespace_BugTest() {
        // BUG: Service không trim searchString trước khi gọi filterClass
        // → "  TOEIC  " được truyền thẳng vào repository thay vì "TOEIC"
        mockUser.setRole(ERole.STUDENT);
        SearchClassDto dto = new SearchClassDto();
        dto.setSearchString("  TOEIC  ");

        Pageable pageable = PageRequest.of(0, 10);
        List<Long> classIds = List.of(100L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classMemberRepository.findClassOfMember(EMAIL)).thenReturn(classIds);
        when(classRepository.filterClass(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(mockClass)));

        classService.getClasses(request, dto, pageable);

        // Capture đối số thực sự được truyền vào filterClass
        ArgumentCaptor<SearchClassDto> captor = ArgumentCaptor.forClass(SearchClassDto.class);
        verify(classRepository).filterClass(captor.capture(), eq(classIds), eq(pageable));

        /*
        * EXPECTED: searchString đã được trim → "TOEIC"
        * THỰC TẾ:  Service không trim → vẫn là "  TOEIC  " → test FAIL → lộ bug
        */
        assertEquals("TOEIC", captor.getValue().getSearchString(),
                "BUG: getClasses() không trim searchString trước khi truyền vào filterClass");
    }

    

    // ============================================================
    // deleteClass()
    // ============================================================

    @Test
    void deleteClass_UserNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.deleteClass(List.of(100L), request));
    }

    @Test
    void deleteClass_ClassNotFound_ThrowsException() {
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.deleteClass(List.of(100L), request));
    }

    @Test
    void deleteClass_IsTeacherOfClass_SetsStatusCancelledAndSaves() {
        // Branch: teacher code matches → true (short-circuit OR) → cancel
        mockUser.setRole(ERole.TEACHER);
        mockClass.setTeacher(mockUser); // same code "U001"

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));

        classService.deleteClass(List.of(100L), request);
        assertEquals(EClassStatus.CANCELLED, mockClass.getStatus());
        verify(classRepository).save(mockClass);
    }

    @Test
    void deleteClass_IsConsultant_SetsStatusCancelledAndSaves() {
        // Branch: teacher code NOT match → false, role=CONSULTANT → true → cancel
        User otherTeacher = new User();
        otherTeacher.setCode("OTHER");
        mockClass.setTeacher(otherTeacher);
        mockUser.setRole(ERole.CONSULTANT);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));

        classService.deleteClass(List.of(100L), request);
        assertEquals(EClassStatus.CANCELLED, mockClass.getStatus());
        verify(classRepository).save(mockClass);
    }
    
    @Test
    void deleteClass_NotTeacherAndNotConsultant_ThrowsNotPermission() {
        // Branch: clazz.teacher.code != user.code (không phải chủ lớp)
        //      && user.role != CONSULTANT
        //      → cả 2 false → throw NOT_PERMISSION (dòng 79)
        User otherTeacher = new User();
        otherTeacher.setCode("OTHER_TEACHER");
        mockClass.setTeacher(otherTeacher); // lớp thuộc giáo viên khác

        mockUser.setRole(ERole.STUDENT); // không phải CONSULTANT

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));

        assertThrows(WebToeicException.class,
                () -> classService.deleteClass(List.of(100L), request),
                "User không phải chủ lớp và không phải CONSULTANT → phải throw NOT_PERMISSION");

        // Đảm bảo không save khi throw
        verify(classRepository, never()).save(any());
    }

    @Test
    void deleteClass_ManagerRoleNotOwner_ShouldSucceed_BugTest() {
        // BUG: Service thiếu check role=MANAGER trong điều kiện xóa lớp
        // → MANAGER không phải chủ lớp bị throw NOT_PERMISSION
        // → đúng ra MANAGER phải được xóa bất kỳ lớp nào
        User otherTeacher = new User();
        otherTeacher.setCode("OTHER_TEACHER");
        mockClass.setTeacher(otherTeacher); // lớp thuộc giáo viên khác

        mockUser.setRole(ERole.MANAGER);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));

        /*
        * EXPECTED: MANAGER được xóa bất kỳ lớp nào → không throw, save() được gọi
        * THỰC TẾ:  Service không check MANAGER → throw NOT_PERMISSION → lộ bug
        */
        assertDoesNotThrow(
                () -> classService.deleteClass(List.of(100L), request),
                "BUG: MANAGER phải được xóa bất kỳ lớp nào — Service thiếu check role MANAGER"
        );
        assertEquals(EClassStatus.CANCELLED, mockClass.getStatus());
        verify(classRepository).save(mockClass);
    }

    @Test
    void deleteClass_MultipleIds_FirstSuccessSecondThrows() {
        // Branch: danh sách nhiều id, id đầu hợp lệ (teacher), id sau không hợp lệ → throw
        User otherTeacher = new User();
        otherTeacher.setCode("OTHER_TEACHER");

        Class mockClass2 = new Class();
        mockClass2.setId(200L);
        mockClass2.setTeacher(otherTeacher); // lớp thứ 2 thuộc giáo viên khác

        mockUser.setRole(ERole.STUDENT);
        mockClass.setTeacher(mockUser); // lớp 100 thuộc mockUser → ok

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(classRepository.findById(200L)).thenReturn(Optional.of(mockClass2));

        assertThrows(WebToeicException.class,
                () -> classService.deleteClass(List.of(100L, 200L), request),
                "id thứ 2 không hợp lệ → throw NOT_PERMISSION");

        // Lớp 100 đã được save trước khi throw ở lớp 200
        verify(classRepository, times(1)).save(mockClass);
        verify(classRepository, never()).save(mockClass2);
    }
    // ============================================================
    // updateClass()
    // ============================================================

    @Test
    void updateClass_UserNotFound_ThrowsException() {
        ClassRequest classRequest = new ClassRequest();
        classRequest.setId(100L);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.updateClass(classRequest, request));
    }

    @Test
    void updateClass_ClassNotFound_ThrowsException() {
        ClassRequest classRequest = new ClassRequest();
        classRequest.setId(100L);
        classRequest.setTeacher(2L);

        User newTeacher = new User();
        newTeacher.setId(2L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.updateClass(classRequest, request));
    }

    @Test
    void updateClass_TeacherNotFound_ThrowsException() {
        ClassRequest classRequest = new ClassRequest();
        classRequest.setId(100L);
        classRequest.setTeacher(99L);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.updateClass(classRequest, request));
    }

    @Test
    void updateClass_WithStatusNull_UsesCurrentStatus() {
        // Branch: classRequest.getStatus() == null → dùng clazz.getStatus() (không gọi fromValue)
        mockClass.setStatus(EClassStatus.PLANNING);
        ClassRequest classRequest = new ClassRequest();
        classRequest.setId(100L);
        classRequest.setTeacher(2L);
        classRequest.setStatus(null); // null → branch: dùng clazz.getStatus()

        User newTeacher = new User();
        newTeacher.setId(2L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newTeacher));
        when(classRepository.save(any())).thenReturn(mockClass);

        classService.updateClass(classRequest, request);
        assertEquals(EClassStatus.PLANNING, mockClass.getStatus()); // giữ nguyên
        verify(classRepository).save(mockClass);
    }

    @Test
    void updateClass_WithStatusNotNull_CallsFromValue() {
        // Branch: classRequest.getStatus() != null → gọi EClassStatus.fromValue(status)
        ClassRequest classRequest = new ClassRequest();
        classRequest.setId(100L);
        classRequest.setName("New Name");
        classRequest.setTeacher(2L);
        classRequest.setStatus(4); // 4 = CANCELLED (không null → gọi fromValue)

        User newTeacher = new User();
        newTeacher.setId(2L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newTeacher));
        when(classRepository.save(any())).thenReturn(mockClass);

        classService.updateClass(classRequest, request);
        assertEquals("New Name", mockClass.getName());
        assertEquals(EClassStatus.CANCELLED, mockClass.getStatus());
        verify(classRepository).save(mockClass);
    }

    @Test
    void updateClass_ManagerRole_ShouldSucceed() {
        mockUser.setRole(ERole.MANAGER);
        ClassRequest req = new ClassRequest();
        req.setId(100L);
        req.setName("Tên mới");
        req.setTeacher(1L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        User t = new User(); t.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(t));
        when(classRepository.save(any())).thenReturn(mockClass);

        assertDoesNotThrow(() -> classService.updateClass(req, request));
        verify(classRepository).save(mockClass);
    }

    @Test
    void updateClass_TeacherOwnerOfClass_ShouldSucceed() {
        // Teacher có code "U001" đang là teacher của mockClass (thiết lập trong setUp)
        mockUser.setRole(ERole.TEACHER);
        ClassRequest req = new ClassRequest();
        req.setId(100L);
        req.setName("Tên mới");
        req.setTeacher(1L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        User t = new User(); t.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(t));
        when(classRepository.save(any())).thenReturn(mockClass);

        assertDoesNotThrow(() -> classService.updateClass(req, request));
        verify(classRepository).save(mockClass);
    }

    @Test
    void updateClass_TeacherNotOwnerOfClass_ShouldThrowNotPermission() {
        // User hiện tại là TEACHER với code "U001"
        // nhưng mockClass.teacher sẽ được đổi sang người khác
        User otherTeacher = new User();
        otherTeacher.setId(99L);
        otherTeacher.setCode("OTHER_TEACHER");
        mockClass.setTeacher(otherTeacher);   // lớp thuộc về giáo viên khác

        mockUser.setRole(ERole.TEACHER);
        ClassRequest req = new ClassRequest();
        req.setId(100L);
        req.setName("Tên mới");
        req.setTeacher(1L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(classRepository.findById(100L)).thenReturn(Optional.of(mockClass));
        User t = new User(); t.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(t));

        /*
            * EXPECTED: TEACHER không phải chủ lớp → không được update → throw.
            * THỰC TẾ:  Service không check ownership → save() được gọi → test FAIL → lộ bug.
            */
        assertThrows(
                WebToeicException.class,
                () -> classService.updateClass(req, request),
                "TEACHER không phải chủ lớp không được phép update — Service phải throw WebToeicException"
        );
        verify(classRepository, never()).save(any());
    }

    // ============================================================
    // createClass()
    // ============================================================

    @Test
    void createClass_UserNotFound_ThrowsException() {
        ClassRequest classRequest = new ClassRequest();
        classRequest.setTeacher(2L);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.createClass(request, classRequest));
    }

    @Test
    void createClass_TeacherNotFound_ThrowsException() {
        ClassRequest classRequest = new ClassRequest();
        classRequest.setTeacher(99L);
        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> classService.createClass(request, classRequest));
    } 

    @Test
    void createClass_ValidData_SavesClassAndMember() {
        ClassRequest classRequest = new ClassRequest();
        classRequest.setTeacher(2L);
        classRequest.setName("TOEIC 500");

        User teacher = new User();
        teacher.setId(2L);

        when(jwtUtil.getEmailFromToken(request)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(classRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        classService.createClass(request, classRequest);

        verify(classRepository).save(any(Class.class));
        verify(classMemberRepository).save(any(ClassMember.class));
    }
}