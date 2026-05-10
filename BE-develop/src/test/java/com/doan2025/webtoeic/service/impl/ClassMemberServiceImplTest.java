package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.EJoinStatus;
import com.doan2025.webtoeic.constants.enums.ENotiType;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.domain.Class;
import com.doan2025.webtoeic.domain.ClassMember;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchMemberInClassDto;
import com.doan2025.webtoeic.dto.request.ClassRequest;
import com.doan2025.webtoeic.dto.response.ClassMemberResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.ClassMemberRepository;
import com.doan2025.webtoeic.repository.ClassRepository;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.ConvertUtil;
import com.doan2025.webtoeic.utils.JwtUtil;
import com.doan2025.webtoeic.utils.NotiUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassMemberServiceImplTest {

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
    @Mock
    private NotiUtils notiUtils;
    @Mock
    private HttpServletRequest request;
    @Mock
    private Pageable pageable;

    @InjectMocks
    private ClassMemberServiceImpl classMemberService;

    private User mockUser;
    private Class mockClass;
    private ClassRequest classRequest;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@gmail.com");

        mockClass = new Class();
        mockClass.setId(100L);

        classRequest = new ClassRequest();
        classRequest.setId(100L);
    }

    // =========================================================================================
    // TESTS FOR getMemberInClass()
    // =========================================================================================

    @Test
    void getMemberInClass_UserNotFound_ThrowsWebToeicException() {
        // Sử dụng builder() thay cho new Object()
        SearchMemberInClassDto searchDto = SearchMemberInClassDto.builder().build();
        
        when(jwtUtil.getEmailFromToken(request)).thenReturn("test@gmail.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, 
                () -> classMemberService.getMemberInClass(request, searchDto, pageable));
    }

    @Test
    void getMemberInClass_RoleStudentAndStatusNull_ReturnsPage() {
        mockUser.setRole(ERole.STUDENT);
        SearchMemberInClassDto searchDto = SearchMemberInClassDto.builder().build();
        searchDto.setStatus(null); // Explicitly null

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockUser.getEmail());
        when(userRepository.findByEmail(mockUser.getEmail())).thenReturn(Optional.of(mockUser));
        
        Page<ClassMember> mockPage = new PageImpl<>(List.of(new ClassMember()));
        when(classMemberRepository.findMembersInClass(searchDto, mockUser.getEmail(), pageable)).thenReturn(mockPage);
        when(convertUtil.convertClassMemberToDto(eq(request), any(ClassMember.class))).thenReturn(new ClassMemberResponse());

        Page<ClassMemberResponse> result = classMemberService.getMemberInClass(request, searchDto, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(classMemberRepository).findMembersInClass(searchDto, mockUser.getEmail(), pageable);
    }

    @Test
    void getMemberInClass_RoleStudentAndStatusEmpty_ReturnsPageAndSetsStatusNull() {
        mockUser.setRole(ERole.STUDENT);
        SearchMemberInClassDto searchDto = SearchMemberInClassDto.builder().build();
        searchDto.setStatus(Collections.emptyList()); // Empty list

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockUser.getEmail());
        when(userRepository.findByEmail(mockUser.getEmail())).thenReturn(Optional.of(mockUser));
        
        Page<ClassMember> mockPage = new PageImpl<>(List.of());
        when(classMemberRepository.findMembersInClass(searchDto, mockUser.getEmail(), pageable)).thenReturn(mockPage);

        classMemberService.getMemberInClass(request, searchDto, pageable);

        assertNull(searchDto.getStatus()); // Verify status is changed to null
        verify(classMemberRepository).findMembersInClass(searchDto, mockUser.getEmail(), pageable);
    }

    @Test
    void getMemberInClass_RoleStudentAndStatusValid_ReturnsPage() {
        mockUser.setRole(ERole.STUDENT);
        SearchMemberInClassDto searchDto = SearchMemberInClassDto.builder().build();
        
        // Truyền vào List chứa string "ACTIVE"
        searchDto.setStatus(List.of("ACTIVE")); 

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockUser.getEmail());
        when(userRepository.findByEmail(mockUser.getEmail())).thenReturn(Optional.of(mockUser));
        
        Page<ClassMember> mockPage = new PageImpl<>(List.of());
        when(classMemberRepository.findMembersInClass(searchDto, mockUser.getEmail(), pageable)).thenReturn(mockPage);

        classMemberService.getMemberInClass(request, searchDto, pageable);

        assertEquals(List.of("ACTIVE"), searchDto.getStatus()); 
        
        verify(classMemberRepository).findMembersInClass(searchDto, mockUser.getEmail(), pageable);
    }

    @Test
    void getMemberInClass_RoleTeacher_ReturnsPageWithNullEmail() {
        mockUser.setRole(ERole.TEACHER); // Not STUDENT
        SearchMemberInClassDto searchDto = SearchMemberInClassDto.builder().build();

        when(jwtUtil.getEmailFromToken(request)).thenReturn(mockUser.getEmail());
        when(userRepository.findByEmail(mockUser.getEmail())).thenReturn(Optional.of(mockUser));
        
        Page<ClassMember> mockPage = new PageImpl<>(List.of(new ClassMember()));
        when(classMemberRepository.findMembersInClass(searchDto, null, pageable)).thenReturn(mockPage);
        when(convertUtil.convertClassMemberToDto(eq(request), any(ClassMember.class))).thenReturn(new ClassMemberResponse());

        Page<ClassMemberResponse> result = classMemberService.getMemberInClass(request, searchDto, pageable);

        assertNotNull(result);
        verify(classMemberRepository).findMembersInClass(searchDto, null, pageable);
    }

    // =========================================================================================
    // TESTS FOR addUserToClass()
    // =========================================================================================

    @Test
    void addUserToClass_ClassNotFound_ThrowsWebToeicException() {
        when(classRepository.findById(classRequest.getId())).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, 
                () -> classMemberService.addUserToClass(request, classRequest));
    }

    @Test
    void addUserToClass_UserNotFound_ThrowsWebToeicException() {
        classRequest.setMemberIds(List.of(1L));
        when(classRepository.findById(classRequest.getId())).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class, 
                () -> classMemberService.addUserToClass(request, classRequest));
        verify(userRepository).findById(1L);
    }

    @Test
    void addUserToClass_UserDropped_StatusUpdatedToActive() {
        classRequest.setMemberIds(List.of(1L));
        ClassMember existingMember = new ClassMember();
        existingMember.setStatus(EJoinStatus.DROPPED); // Status is DROPPED

        when(classRepository.findById(classRequest.getId())).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(classMemberRepository.findByClassAndMember(1L, 100L)).thenReturn(existingMember);

        classMemberService.addUserToClass(request, classRequest);

        assertEquals(EJoinStatus.ACTIVE, existingMember.getStatus());
        verify(classMemberRepository).save(existingMember);
        verifyNoInteractions(notiUtils);
    }

    @Test
    void addUserToClass_UserActive_DoesNothing() {
        classRequest.setMemberIds(List.of(1L));
        ClassMember existingMember = new ClassMember();
        existingMember.setStatus(EJoinStatus.ACTIVE); // Status is ACTIVE

        when(classRepository.findById(classRequest.getId())).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(classMemberRepository.findByClassAndMember(1L, 100L)).thenReturn(existingMember);

        classMemberService.addUserToClass(request, classRequest);

        assertEquals(EJoinStatus.ACTIVE, existingMember.getStatus());
        verify(classMemberRepository, never()).save(any()); // Save should not be called

        verifyNoInteractions(notiUtils);
    }

    @Test
    void addUserToClass_UserNew_SavesAndSendsNotification() {
        classRequest.setMemberIds(List.of(1L));

        when(classRepository.findById(classRequest.getId())).thenReturn(Optional.of(mockClass));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(classMemberRepository.findByClassAndMember(1L, 100L)).thenReturn(null); // User hasn't joined before

        classMemberService.addUserToClass(request, classRequest);

        ArgumentCaptor<ClassMember> captor = ArgumentCaptor.forClass(ClassMember.class);
        verify(classMemberRepository).save(captor.capture());
        assertEquals(EJoinStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(mockUser, captor.getValue().getMember());
        assertEquals(mockClass, captor.getValue().getClazz());

        verify(notiUtils).sendNoti(
                eq(List.of(mockUser)), 
                eq(ENotiType.ADD_TO_CLASS), 
                eq(Constants.ADD_TO_CLASS_CONTENT), 
                eq(Constants.ADD_TO_CLASS_CONTENT), 
                eq(100L)
        );
    }

    @Test
    void addUserToClass_EmptyMemberIds_CompletesSuccessfully() {
        classRequest.setMemberIds(new ArrayList<>());
        when(classRepository.findById(classRequest.getId())).thenReturn(Optional.of(mockClass));

        assertDoesNotThrow(() -> classMemberService.addUserToClass(request, classRequest));
        verify(userRepository, never()).findById(anyLong());
    }

    // =========================================================================================
    // TESTS FOR removeUserFromClass()
    // =========================================================================================

    @Test
    void removeUserFromClass_UserListEmpty_ThrowsWebToeicException() {
        when(classMemberRepository.findByClassAndUser(classRequest)).thenReturn(Collections.emptyList());

        assertThrows(WebToeicException.class, 
                () -> classMemberService.removeUserFromClass(request, classRequest));
    }

    @Test
    void removeUserFromClass_UserListNotEmpty_UpdatesStatusToDropped() {
        ClassMember member1 = new ClassMember();
        member1.setStatus(EJoinStatus.ACTIVE);
        ClassMember member2 = new ClassMember();
        member2.setStatus(EJoinStatus.ACTIVE);

        List<ClassMember> classMemberList = List.of(member1, member2);

        when(classMemberRepository.findByClassAndUser(classRequest)).thenReturn(classMemberList);

        classMemberService.removeUserFromClass(request, classRequest);

        assertEquals(EJoinStatus.DROPPED, member1.getStatus());
        assertEquals(EJoinStatus.DROPPED, member2.getStatus());
        verify(classMemberRepository, times(2)).save(any(ClassMember.class)); // 2 members -> saved 2 times
    }
}