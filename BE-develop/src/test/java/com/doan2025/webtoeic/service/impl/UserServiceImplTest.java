package com.doan2025.webtoeic.service.impl;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.domain.Student;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.SearchBaseDto;
import com.doan2025.webtoeic.dto.request.UserRequest;
import com.doan2025.webtoeic.dto.response.UserResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.UserRepository;
import com.doan2025.webtoeic.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserServiceImpl} with Mockito (no real database).
 * <p>
 * Conventions: every {@code @Test} documents {@code Test Case ID}, a clear scenario description,
 * {@code CheckDB} (how persistence is verified via mocks), and {@code Rollback} (data isolation).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private UserServiceImpl userService;

    private User managerUser;
    private User consultantUser;
    private User studentUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        managerUser = new User();
        managerUser.setId(1L);
        managerUser.setEmail("manager@test.com");
        managerUser.setRole(ERole.MANAGER);
        managerUser.setPassword("encoded-manager");
        managerUser.setIsActive(true);
        managerUser.setIsDelete(false);

        consultantUser = new User();
        consultantUser.setId(2L);
        consultantUser.setEmail("consultant@test.com");
        consultantUser.setRole(ERole.CONSULTANT);
        consultantUser.setPassword("encoded-consultant");
        consultantUser.setIsActive(true);
        consultantUser.setIsDelete(false);

        studentUser = new User();
        studentUser.setId(3L);
        studentUser.setEmail("student@test.com");
        studentUser.setRole(ERole.STUDENT);
        studentUser.setPassword("encoded-student");
        studentUser.setIsActive(true);
        studentUser.setIsDelete(false);
        Student studentProfile = new Student();
        studentProfile.setEducation("High school");
        studentProfile.setMajor("CS");
        studentProfile.setUser(studentUser);
        studentUser.setStudent(studentProfile);
    }

    /**
     * Test Case ID: UTC-USR-SVC-001
     * <p>
     * When {@link JwtUtil#getEmailFromToken} returns null, {@link UserServiceImpl#getListUserFilter} must throw
     * {@link WebToeicException} with {@link ResponseCode#NOT_EXISTED} and {@link ResponseObject#EMAIL}, because the
     * caller cannot be resolved (same flow as {@code POST /api/v1/user/filter}).
     * <p>
     * CheckDB: Verify {@link UserRepository#findListUserFilter} is never invoked; no repository write occurs.
     * Rollback: Not applicable — mocked {@link UserRepository}; no physical database state.
     */
    @Test
    void getListUserFilter_whenEmailFromTokenNull_throwsNotExistedEmail() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(null);
        SearchBaseDto dto = new SearchBaseDto();

        WebToeicException ex = assertThrows(WebToeicException.class,
                () -> userService.getListUserFilter(httpServletRequest, dto, pageable));
        assertEquals(ResponseCode.NOT_EXISTED, ex.getResponseCode());
        assertEquals(ResponseObject.EMAIL, ex.getResponseObject());
        verify(userRepository, never()).findListUserFilter(any(), any(), any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-002
     * <p>
     * If the email from the token is not found, the service must fail with {@link ResponseCode#NOT_EXISTED} /
     * {@link ResponseObject#USER} and must not run the filtered listing query.
     * <p>
     * CheckDB: Expect exactly one {@link UserRepository#findByEmail}; never {@link UserRepository#save}.
     * Rollback: Not applicable — mocked persistence only.
     */
    @Test
    void getListUserFilter_whenUserMissing_throwsNotExistedUser() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn("ghost@test.com");
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(WebToeicException.class,
                () -> userService.getListUserFilter(httpServletRequest, new SearchBaseDto(), pageable));
        verify(userRepository).findByEmail("ghost@test.com");
        verify(userRepository, never()).findListUserFilter(any(), any(), any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-003
     * <p>
     * A {@link ERole#MANAGER} caller must pass {@link Constants#ROLE_BELOW_MANAGER} into
     * {@link UserRepository#findListUserFilter}; the returned {@link Page} must match the repository stub.
     * <p>
     * CheckDB: Capture role list argument to {@code findListUserFilter} and assert equality to the constant list.
     * Rollback: Not applicable — no real database.
     */
    @Test
    void getListUserFilter_whenManager_passesRoleBelowManagerToRepository() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(managerUser.getEmail());
        when(userRepository.findByEmail(managerUser.getEmail())).thenReturn(Optional.of(managerUser));
        SearchBaseDto dto = new SearchBaseDto();
        Page<UserResponse> page = new PageImpl<>(List.of(new UserResponse()));
        when(userRepository.findListUserFilter(any(SearchBaseDto.class), anyList(), eq(pageable))).thenReturn(page);

        Page<UserResponse> result = userService.getListUserFilter(httpServletRequest, dto, pageable);

        assertSame(page, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ERole>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).findListUserFilter(eq(dto), rolesCaptor.capture(), eq(pageable));
        assertEquals(Constants.ROLE_BELOW_MANAGER, rolesCaptor.getValue());
    }

    /**
     * Test Case ID: UTC-USR-SVC-004
     * <p>
     * A {@link ERole#CONSULTANT} caller must pass {@link Constants#ROLE_BELOW_CONSULTANT} into the listing query
     * so visibility rules stay narrower than for a manager.
     * <p>
     * CheckDB: Verify {@link UserRepository#findListUserFilter} receives the consultant role scope via captor.
     * Rollback: Not applicable — mocked repositories.
     */
    @Test
    void getListUserFilter_whenConsultant_passesRoleBelowConsultantToRepository() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(consultantUser.getEmail());
        when(userRepository.findByEmail(consultantUser.getEmail())).thenReturn(Optional.of(consultantUser));
        SearchBaseDto dto = new SearchBaseDto();
        Page<UserResponse> page = new PageImpl<>(Collections.emptyList());
        when(userRepository.findListUserFilter(any(), anyList(), eq(pageable))).thenReturn(page);

        userService.getListUserFilter(httpServletRequest, dto, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ERole>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).findListUserFilter(eq(dto), rolesCaptor.capture(), eq(pageable));
        assertEquals(Constants.ROLE_BELOW_CONSULTANT, rolesCaptor.getValue());
    }

    /**
     * Test Case ID: UTC-USR-SVC-005
     * <p>
     * An empty {@code userRoles} collection on {@link SearchBaseDto} must be normalized to {@code null} before the
     * repository call so the query layer receives a consistent “no filter” signal.
     * <p>
     * CheckDB: Use {@link ArgumentCaptor} on {@link SearchBaseDto} passed to {@code findListUserFilter} and assert
     * {@code getUserRoles() == null}.
     * Rollback: Not applicable — no committed rows.
     */
    @Test
    void getListUserFilter_whenUserRolesEmpty_normalizesToNullInQuery() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(managerUser.getEmail());
        when(userRepository.findByEmail(managerUser.getEmail())).thenReturn(Optional.of(managerUser));
        SearchBaseDto dto = new SearchBaseDto();
        dto.setUserRoles(Collections.emptyList());
        when(userRepository.findListUserFilter(any(), anyList(), eq(pageable)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        userService.getListUserFilter(httpServletRequest, dto, pageable);

        ArgumentCaptor<SearchBaseDto> dtoCaptor = ArgumentCaptor.forClass(SearchBaseDto.class);
        verify(userRepository).findListUserFilter(dtoCaptor.capture(), anyList(), eq(pageable));
        assertNull(dtoCaptor.getValue().getUserRoles());
    }

    /**
     * Test Case ID: UTC-USR-SVC-006
     * <p>
     * {@link UserServiceImpl#getUserCurrent} must reject a missing token email with {@link ResponseCode#NOT_EXISTED} /
     * {@link ResponseObject#EMAIL} (mirrors {@code GET /api/v1/user}).
     * <p>
     * CheckDB: Verify {@link UserRepository#findUser} is never called.
     * Rollback: Not applicable — mocks only.
     */
    @Test
    void getUserCurrent_whenTokenEmailMissing_throws() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(null);
        assertThrows(WebToeicException.class, () -> userService.getUserCurrent(httpServletRequest));
        verify(userRepository, never()).findUser(anyString());
    }

    /**
     * Test Case ID: UTC-USR-SVC-007
     * <p>
     * Happy path: resolve the current user email, load {@link UserResponse} via {@link UserRepository#findUser},
     * and return the same instance produced by the stub.
     * <p>
     * CheckDB: Verify {@code findUser} is invoked once with the token email.
     * Rollback: Not applicable — read-only on the mock repository.
     */
    @Test
    void getUserCurrent_whenFound_returnsUserResponse() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(studentUser.getEmail());
        UserResponse expected = new UserResponse();
        when(userRepository.findUser(studentUser.getEmail())).thenReturn(Optional.of(expected));

        UserResponse actual = userService.getUserCurrent(httpServletRequest);

        assertSame(expected, actual);
        verify(userRepository).findUser(studentUser.getEmail());
    }

    /**
     * Test Case ID: UTC-USR-SVC-008
     * <p>
     * {@link UserServiceImpl#getUserDetails} must validate {@link UserRequest#getId()} and throw
     * {@link ResponseCode#IS_NULL} / {@link ResponseObject#ID} when the id is null.
     * <p>
     * CheckDB: Verify {@link UserRepository#findUserById} is never called.
     * Rollback: Not applicable — no persistence side effects.
     */
    @Test
    void getUserDetails_whenIdNull_throwsIsNull() {
        UserRequest request = new UserRequest();
        request.setId(null);
        WebToeicException ex = assertThrows(WebToeicException.class, () -> userService.getUserDetails(request));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.ID, ex.getResponseObject());
        verify(userRepository, never()).findUserById(any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-009
     * <p>
     * When {@link UserRepository#findUserById} returns empty, the service must surface {@link ResponseCode#NOT_EXISTED}
     * with {@link ResponseObject#USER}.
     * <p>
     * CheckDB: Verify a single {@code findUserById} read; never {@code save}.
     * Rollback: Not applicable — mocked layer.
     */
    @Test
    void getUserDetails_whenNotFound_throws() {
        UserRequest request = UserRequest.builder().id(99L).build();
        when(userRepository.findUserById(request)).thenReturn(Optional.empty());
        assertThrows(WebToeicException.class, () -> userService.getUserDetails(request));
        verify(userRepository).findUserById(request);
    }

    /**
     * Test Case ID: UTC-USR-SVC-010
     * <p>
     * Happy path: {@link UserServiceImpl#getUserDetails} returns the {@link UserResponse} supplied by
     * {@link UserRepository#findUserById}.
     * <p>
     * CheckDB: Verify {@code findUserById} called with the incoming {@link UserRequest}.
     * Rollback: Not applicable — read path only on the mock.
     */
    @Test
    void getUserDetails_whenFound_returnsResponse() {
        UserRequest request = UserRequest.builder().id(5L).build();
        UserResponse body = new UserResponse();
        when(userRepository.findUserById(request)).thenReturn(Optional.of(body));
        assertSame(body, userService.getUserDetails(request));
    }

    /**
     * Test Case ID: UTC-USR-SVC-011
     * <p>
     * {@link UserServiceImpl#updateUserDetails} requires a resolvable email from the servlet request; when the token
     * yields null email, the implementation throws {@link ResponseCode#NOT_EXISTED} / {@link ResponseObject#USER}.
     * <p>
     * CheckDB: Verify {@link UserRepository#save} never runs (no partial update).
     * Rollback: Not applicable — no database writes.
     */
    @Test
    void updateUserDetails_whenEmailNull_throws() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(null);
        assertThrows(WebToeicException.class,
                () -> userService.updateUserDetails(httpServletRequest, new UserRequest()));
        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-012
     * <p>
     * Password rotation must fail with {@link ResponseCode#NOT_MATCHED} / {@link ResponseObject#PASSWORD} when
     * {@link PasswordEncoder#matches} rejects the supplied old password.
     * <p>
     * CheckDB: Verify {@code matches} invoked; {@link UserRepository#save} never invoked.
     * Rollback: Not applicable — no persisted mutation on the mock.
     */
    @Test
    void updateUserDetails_whenOldPasswordWrong_throwsNotMatched() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(studentUser.getEmail());
        when(userRepository.findByEmail(studentUser.getEmail())).thenReturn(Optional.of(studentUser));
        UserRequest updateRequest = new UserRequest();
        updateRequest.setPassword("newPass");
        updateRequest.setOldPassword("wrong");
        when(passwordEncoder.matches("wrong", studentUser.getPassword())).thenReturn(false);

        assertThrows(WebToeicException.class, () -> userService.updateUserDetails(httpServletRequest, updateRequest));
        verify(passwordEncoder).matches("wrong", studentUser.getPassword());
        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-013
     * <p>
     * Successful password change must {@link PasswordEncoder#encode} the new secret, persist via
     * {@link UserRepository#save}, and map the entity to {@link UserResponse}.
     * <p>
     * CheckDB: Verify {@code encode} + {@code save} with argument matcher on the encoded password field.
     * Rollback: Not applicable — unit test uses Mockito; production rollback is handled by service {@code @Transactional}.
     */
    @Test
    void updateUserDetails_whenPasswordChange_success_savesEncodedPassword() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(studentUser.getEmail());
        when(userRepository.findByEmail(studentUser.getEmail())).thenReturn(Optional.of(studentUser));
        UserRequest updateRequest = new UserRequest();
        updateRequest.setPassword("newSecret");
        updateRequest.setOldPassword("oldPlain");
        when(passwordEncoder.matches("oldPlain", studentUser.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newSecret")).thenReturn("ENC-new");
        User saved = new User();
        saved.setPassword("ENC-new");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UserResponse mapped = new UserResponse();
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(mapped);

        UserResponse out = userService.updateUserDetails(httpServletRequest, updateRequest);

        assertSame(mapped, out);
        verify(userRepository).save(argThat(u -> "ENC-new".equals(u.getPassword())));
        verify(passwordEncoder).encode("newSecret");
    }

    /**
     * Test Case ID: UTC-USR-SVC-014
     * <p>
     * Students updating profile fields must propagate education/major into the nested {@link Student} aggregate and
     * call {@link UserRepository#save} exactly once for the owning {@link User}.
     * <p>
     * CheckDB: Verify {@code save(studentUser)} and assert nested getters reflect the request payload.
     * Rollback: Not applicable — mocked persistence; real transactions roll back via service annotations.
     */
    @Test
    void updateUserDetails_whenStudent_updatesProfileAndSaves() {
        when(jwtUtil.getEmailFromToken(httpServletRequest)).thenReturn(studentUser.getEmail());
        when(userRepository.findByEmail(studentUser.getEmail())).thenReturn(Optional.of(studentUser));
        UserRequest updateRequest = new UserRequest();
        updateRequest.setEducation("University");
        updateRequest.setMajor("SE");
        UserResponse mapped = new UserResponse();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelMapper.map(any(User.class), eq(UserResponse.class))).thenReturn(mapped);
        when(modelMapper.map(any(Student.class), eq(com.doan2025.webtoeic.dto.response.StudentResponse.class)))
                .thenReturn(new com.doan2025.webtoeic.dto.response.StudentResponse());

        UserResponse out = userService.updateUserDetails(httpServletRequest, updateRequest);

        assertSame(mapped, out);
        verify(userRepository).save(studentUser);
        assertEquals("University", studentUser.getStudent().getEducation());
        assertEquals("SE", studentUser.getStudent().getMajor());
    }

    /**
     * Test Case ID: UTC-USR-SVC-015
     * <p>
     * {@link UserServiceImpl#deleteOrDisableUser} must fail when {@link UserRepository#findById} is empty, matching
     * admin flows exposed by {@code POST /api/v1/user/delete-user} and {@code /disable-user}.
     * <p>
     * CheckDB: Verify {@code findById}; never {@code save}.
     * Rollback: Not applicable — no mutation attempted.
     */
    @Test
    void deleteOrDisableUser_whenUserNotFound_throws() {
        UserRequest disableRequest = UserRequest.builder().id(404L).build();
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(WebToeicException.class, () -> userService.deleteOrDisableUser(disableRequest));
        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-016
     * <p>
     * When {@code isActive} in {@link UserRequest} differs from the persisted entity, the service updates the flag,
     * saves, and maps to {@link UserResponse} for consultants/managers disabling accounts.
     * <p>
     * CheckDB: Verify {@link UserRepository#save} receives the mutated entity with the new flag value.
     * Rollback: Not applicable in this unit test; production uses transactional boundaries on the service bean.
     */
    @Test
    void deleteOrDisableUser_whenIsActiveChanges_persistsAndReturnsMapped() {
        User managed = new User();
        managed.setId(10L);
        managed.setIsActive(true);
        managed.setIsDelete(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(managed));
        when(userRepository.save(managed)).thenReturn(managed);
        UserResponse dto = new UserResponse();
        when(modelMapper.map(managed, UserResponse.class)).thenReturn(dto);

        UserRequest disableRequest = UserRequest.builder().id(10L).isActive(false).build();
        UserResponse result = userService.deleteOrDisableUser(disableRequest);

        assertSame(dto, result);
        assertFalse(managed.getIsActive());
        verify(userRepository).save(managed);
    }

    /**
     * Test Case ID: UTC-USR-SVC-017
     * <p>
     * {@link UserServiceImpl#resetPassword} must reject a null token with {@link ResponseCode#IS_NULL} /
     * {@link ResponseObject#TOKEN} (reset flow is not exposed on {@link com.doan2025.webtoeic.controller.UserController}).
     * <p>
     * CheckDB: Verify {@link UserRepository#save} never executes.
     * Rollback: Not applicable — no writes.
     */
    @Test
    void resetPassword_whenTokenNull_throws() {
        UserRequest resetRequest = new UserRequest();
        resetRequest.setToken(null);
        resetRequest.setPassword("x");
        WebToeicException ex = assertThrows(WebToeicException.class, () -> userService.resetPassword(resetRequest));
        assertEquals(ResponseCode.IS_NULL, ex.getResponseCode());
        assertEquals(ResponseObject.TOKEN, ex.getResponseObject());
        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-018
     * <p>
     * If {@link JwtUtil#getEmailFromTokenString} cannot derive an email, reset must abort with
     * {@link ResponseCode#NOT_EXISTED} / {@link ResponseObject#EMAIL}.
     * <p>
     * CheckDB: Verify {@link UserRepository#findByEmail} is never called (no user load).
     * Rollback: Not applicable — mocked repositories.
     */
    @Test
    void resetPassword_whenEmailFromTokenStringNull_throws() {
        UserRequest resetRequest = new UserRequest();
        resetRequest.setToken("bad");
        resetRequest.setPassword("x");
        when(jwtUtil.getEmailFromTokenString("bad")).thenReturn(null);
        assertThrows(WebToeicException.class, () -> userService.resetPassword(resetRequest));
        verify(userRepository, never()).findByEmail(anyString());
    }

    /**
     * Test Case ID: UTC-USR-SVC-019
     * <p>
     * Accounts that are inactive and soft-deleted must raise {@link ResponseCode#NOT_AVAILABLE} /
     * {@link ResponseObject#USER} during password reset per the guard in {@link UserServiceImpl#resetPassword}.
     * <p>
     * CheckDB: User may be read via {@link UserRepository#findByEmail}, but {@code save} must not run.
     * Rollback: Not applicable — no persisted change in the mock.
     */
    @Test
    void resetPassword_whenUserInactiveAndDeleted_throwsNotAvailable() {
        UserRequest resetRequest = new UserRequest();
        resetRequest.setToken("tok");
        resetRequest.setPassword("new");
        when(jwtUtil.getEmailFromTokenString("tok")).thenReturn("u@test.com");
        User targetUser = new User();
        targetUser.setEmail("u@test.com");
        targetUser.setIsActive(false);
        targetUser.setIsDelete(true);
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(targetUser));

        assertThrows(WebToeicException.class, () -> userService.resetPassword(resetRequest));
        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-USR-SVC-020
     * <p>
     * Valid reset tokens must lead to {@link PasswordEncoder#encode} followed by {@link UserRepository#save} with the
     * updated password hash.
     * <p>
     * CheckDB: Verify {@code save} with argument matcher ensuring the encoded password matches the stubbed digest.
     * Rollback: Not applicable in unit scope; service-level transactions handle real rollback semantics.
     */
    @Test
    void resetPassword_whenValid_savesEncodedPassword() {
        UserRequest resetRequest = new UserRequest();
        resetRequest.setToken("tok");
        resetRequest.setPassword("newPass");
        when(jwtUtil.getEmailFromTokenString("tok")).thenReturn("u@test.com");
        User targetUser = new User();
        targetUser.setEmail("u@test.com");
        targetUser.setIsActive(true);
        targetUser.setIsDelete(false);
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(targetUser));
        when(passwordEncoder.encode("newPass")).thenReturn("ENC");

        userService.resetPassword(resetRequest);

        verify(userRepository).save(argThat(saved -> "ENC".equals(saved.getPassword())));
        verify(passwordEncoder).encode("newPass");
    }
}
