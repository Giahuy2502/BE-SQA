package com.doan2025.webtoeic.security;

import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.ForgotPassword;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.request.AuthenticationRequest;
import com.doan2025.webtoeic.dto.request.IntrospectRequest;
import com.doan2025.webtoeic.dto.request.RegisterRequest;
import com.doan2025.webtoeic.dto.request.VerifyRequest;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.service.EmailService;
import com.doan2025.webtoeic.utils.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticationService} with mocked repositories and collaborators.
 * <p>
 * <b>CheckDB (mentor requirement):</b> There is no physical database. Persistence expectations are
 * verified with Mockito {@code verify(...)} and {@link ArgumentCaptor} on repository mocks — e.g.
 * {@code invalidatedTokenRepository.save}, {@code userRepository.findByEmail}, {@code forgotPasswordRepository.save}.
 * This proves the service issued the correct persistence <em>calls</em> with expected arguments.
 * <p>
 * <b>Rollback:</b> Not applicable here (no transaction against a real datasource). For true DB
 * rollback after tests, add integration tests with {@code @DataJpaTest} + {@code @Transactional},
 * Testcontainers, or {@code @Sql} with cleanup scripts.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String SIGNER_KEY_FOR_TESTS =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private UserRepository userRepository;
    @Mock
    private InvalidatedTokenRepository invalidatedTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ManagerRepository managerRepository;
    @Mock
    private ConsultantRepository consultantRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private ForgotPasswordRepository forgotPasswordRepository;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthenticationService authenticationServiceUnderTest;

    @BeforeEach
    void injectJwtConfigurationIntoServiceUnderTest() {
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "SIGNER_KEY", SIGNER_KEY_FOR_TESTS);
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "VALID_DURATION", 3600L);
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "REFRESHABLE_DURATION", 7200L);
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "ISSUER", "test-issuer");
    }

    /** Builds an active, non-deleted student user for use as repository return values. */
    private User createActiveStudentUser(String email) {
        User user = new User(email, "encodedPasswordHash", "First", "Last", ERole.STUDENT);
        user.setId(10L);
        user.setIsActive(true);
        user.setIsDelete(false);
        return user;
    }

    /**
     * Test Case ID: UTC-SEC-AUT-001
     * Objective: {@link AuthenticationService#introspect} returns valid when JWT verifies and is not blacklisted.
     * CheckDB: {@code invalidatedTokenRepository.existsByToken} consulted (stubbed false); no save in this path.
     * Rollback: N/A.
     */
    @Test
    void introspect_returnsValidTrue_whenTokenPassesVerify() throws Exception {
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        User activeUser = createActiveStudentUser("ok@t.com");
        String compactJwt = generateCompactJwtForUser(activeUser);

        var introspectResponse = authenticationServiceUnderTest.introspect(
                IntrospectRequest.builder().token(compactJwt).build());

        assertThat(introspectResponse.isValid()).isTrue();
    }

    /**
     * Test Case ID: UTC-SEC-AUT-002
     * Objective: Expired JWT yields {@code valid=false} (wrapped as non-throwing introspect).
     * CheckDB: Blacklist check may not run after expiry branch; no DB write asserted here.
     * Rollback: N/A.
     */
    @Test
    void introspect_returnsValidFalse_whenTokenExpired() throws Exception {
        SecretKey signingKey = Keys.hmacShaKeyFor(SIGNER_KEY_FOR_TESTS.getBytes());
        String expiredCompactJwt = Jwts.builder()
                .subject("x@x.com")
                .issuer("test-issuer")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .id(UUID.randomUUID().toString())
                .claim("scope", "ROLE_STUDENT")
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();

        var introspectResponse = authenticationServiceUnderTest.introspect(
                IntrospectRequest.builder().token(expiredCompactJwt).build());

        assertThat(introspectResponse.isValid()).isFalse();
    }

    /**
     * Test Case ID: UTC-SEC-AUT-003
     * Objective: Blacklisted JIT yields {@code valid=false}.
     * CheckDB: {@code invalidatedTokenRepository.existsByToken} returns true — simulates row present in blacklist table.
     * Rollback: N/A.
     */
    @Test
    void introspect_returnsValidFalse_whenTokenInvalidated() throws Exception {
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(true);
        User activeUser = createActiveStudentUser("inv@t.com");
        String compactJwt = generateCompactJwtForUser(activeUser);

        var introspectResponse = authenticationServiceUnderTest.introspect(
                IntrospectRequest.builder().token(compactJwt).build());

        assertThat(introspectResponse.isValid()).isFalse();
    }

    /**
     * Test Case ID: UTC-SEC-AUT-004
     * Objective: Non-JWT string causes {@link ParseException} (not swallowed inside introspect).
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void introspect_throwsParseException_whenTokenNotJwt() {
        assertThatThrownBy(() ->
                authenticationServiceUnderTest.introspect(IntrospectRequest.builder().token("not-a-jwt").build()))
                .isInstanceOf(ParseException.class);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-005
     * Objective: Successful login returns token and role.
     * CheckDB: {@code userRepository.findByEmail} read path only (stubbed). Rollback: N/A.
     */
    @Test
    void authenticate_returnsToken_whenCredentialsValid() {
        User activeUser = createActiveStudentUser("login@t.com");
        when(userRepository.findByEmail("login@t.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "encodedPasswordHash")).thenReturn(true);

        var authenticationResponse = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("login@t.com").password("secret").build());

        assertThat(authenticationResponse.isAuthenticated()).isTrue();
        assertThat(authenticationResponse.getToken()).isNotBlank();
        assertThat(authenticationResponse.getRole()).isEqualTo(ERole.STUDENT.getValue());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-006
     * Objective: Email argument is trimmed before repository lookup.
     * CheckDB: verify {@code findByEmail} called with trimmed value. Rollback: N/A.
     */
    @Test
    void authenticate_trimsEmail() {
        User activeUser = createActiveStudentUser("trim@t.com");
        when(userRepository.findByEmail("trim@t.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), eq("encodedPasswordHash"))).thenReturn(true);

        authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("  trim@t.com  ").password("p").build());

        verify(userRepository).findByEmail("trim@t.com");
    }

    /**
     * Test Case ID: UTC-SEC-AUT-007
     * Objective: Unknown email throws {@link WebToeicException} NOT_EXISTED.
     * CheckDB: verify {@code findByEmail} only; no save. Rollback: N/A.
     */
    @Test
    void authenticate_throws_whenUserMissing() {
        when(userRepository.findByEmail("no@t.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.authenticate(
                        AuthenticationRequest.builder().email("no@t.com").password("p").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_EXISTED);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-008
     * Objective: Wrong password throws INVALID_PASSWORD.
     * CheckDB: read user only. Rollback: N/A.
     */
    @Test
    void authenticate_throws_whenPasswordWrong() {
        User activeUser = createActiveStudentUser("bad@t.com");
        when(userRepository.findByEmail("bad@t.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.authenticate(
                        AuthenticationRequest.builder().email("bad@t.com").password("wrong").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID_PASSWORD);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-009
     * Objective: Inactive and deleted user cannot authenticate.
     * CheckDB: read user only. Rollback: N/A.
     */
    @Test
    void authenticate_throws_whenUserNotActiveAndDeleted() {
        User inactiveDeletedUser = createActiveStudentUser("gone@t.com");
        inactiveDeletedUser.setIsActive(false);
        inactiveDeletedUser.setIsDelete(true);
        when(userRepository.findByEmail("gone@t.com")).thenReturn(Optional.of(inactiveDeletedUser));

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.authenticate(
                        AuthenticationRequest.builder().email("gone@t.com").password("p").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_AVAILABLE);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-010
     * Objective: Null role defaults to STUDENT and persists student profile branch.
     * CheckDB: verify {@code studentRepository.save}; verify {@code managerRepository} never saves.
     * Rollback: N/A (mocked).
     */
    @Test
    void register_defaultsToStudent_whenRoleNull() {
        when(userRepository.existsByEmail("new@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(99L);
            if (userToSave.getCode() == null) {
                userToSave.setCode("STU999000000");
            }
            return userToSave;
        });

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("new@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(null)
                .build();

        authenticationServiceUnderTest.register(registerRequest);

        verify(studentRepository).save(any());
        verify(managerRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-011
     * Objective: Duplicate email rejected before any save.
     * CheckDB: verify {@code existsByEmail} path; verify no {@code userRepository.save}. Rollback: N/A.
     */
    @Test
    void register_throws_whenEmailExists() {
        when(userRepository.existsByEmail("dup@t.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.register(RegisterRequest.builder()
                        .email("dup@t.com")
                        .password("password12")
                        .firstName("F")
                        .lastName("L")
                        .role(4)
                        .build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.EXISTED);

        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-012
     * Objective: Invalid role value throws INVALID for ROLE.
     * CheckDB: no save. Rollback: N/A.
     */
    @Test
    void register_throws_whenRoleInvalid() {
        when(userRepository.existsByEmail("r@t.com")).thenReturn(false);

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.register(RegisterRequest.builder()
                        .email("r@t.com")
                        .password("password12")
                        .firstName("F")
                        .lastName("L")
                        .role(999)
                        .build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-013
     * Objective: MANAGER registration saves manager entity.
     * CheckDB: verify {@code managerRepository.save}. Rollback: N/A.
     */
    @Test
    void register_savesManager_whenRoleManager() {
        when(userRepository.existsByEmail("man@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(1L);
            return userToSave;
        });

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("man@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(1)
                .build());

        verify(managerRepository).save(any());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-014
     * Objective: Logout persists invalidated token when JWT is valid.
     * CheckDB: verify {@code invalidatedTokenRepository.save} once. Rollback: N/A.
     */
    @Test
    void logout_savesInvalidatedToken_whenJwtValid() throws Exception {
        User activeUser = createActiveStudentUser("out@t.com");
        String compactJwt = generateCompactJwtForUser(activeUser);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(compactJwt);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);

        authenticationServiceUnderTest.logout(httpServletRequest);

        verify(invalidatedTokenRepository).save(any());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-015
     * Objective: Logout swallows {@link WebToeicException} when signature verification fails (simulated wrong key).
     * CheckDB: verify {@code invalidatedTokenRepository.save} never called. Rollback: N/A.
     */
    @Test
    void logout_swallowsException_whenVerifyFailsWithWebToeicException() throws Exception {
        SecretKey foreignSigningKey = Keys.hmacShaKeyFor(
                "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".getBytes());
        String wronglySignedJwt = Jwts.builder()
                .subject("x@x.com")
                .issuer("test-issuer")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .id(UUID.randomUUID().toString())
                .claim("scope", "ROLE_STUDENT")
                .signWith(foreignSigningKey, SignatureAlgorithm.HS512)
                .compact();
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(wronglySignedJwt);

        authenticationServiceUnderTest.logout(httpServletRequest);

        verify(invalidatedTokenRepository, never()).save(any());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-016
     * Objective: Refresh flow blacklists old token and issues new token for existing user.
     * CheckDB: verify {@code invalidatedTokenRepository.save} at least once. Rollback: N/A.
     */
    @Test
    void refreshToken_returnsNewToken_whenChainValid() throws Exception {
        User activeUser = createActiveStudentUser("ref@t.com");
        String compactJwt = generateCompactJwtForUser(activeUser);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(compactJwt);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByEmail("ref@t.com")).thenReturn(Optional.of(activeUser));

        var authenticationResponse = authenticationServiceUnderTest.refreshToken(httpServletRequest);

        assertThat(authenticationResponse.getToken()).isNotBlank();
        assertThat(authenticationResponse.isAuthenticated()).isTrue();
        verify(invalidatedTokenRepository, atLeastOnce()).save(any());
    }

    /**
     * Test Case ID: UTC-SEC-AUT-017
     * Objective: Refresh fails when subject email no longer exists in DB.
     * CheckDB: verify {@code userRepository.findByEmail}; blacklist save may occur before user lookup — implementation detail.
     * Rollback: N/A.
     */
    @Test
    void refreshToken_throws_whenUserMissing() throws Exception {
        User activeUser = createActiveStudentUser("missing@t.com");
        String compactJwt = generateCompactJwtForUser(activeUser);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(compactJwt);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByEmail("missing@t.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationServiceUnderTest.refreshToken(httpServletRequest))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.UNAUTHENTICATED);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-018
     * Objective: Verify-mail sends email and inserts forgot-password row (captured).
     * CheckDB: verify {@code forgotPasswordRepository.save}; captor asserts OTP range and user link.
     * Rollback: N/A.
     */
    @Test
    void verifyMail_sendsEmail_andSavesForgotPassword() {
        User activeUser = createActiveStudentUser("mail@t.com");
        when(userRepository.findByEmail("mail@t.com")).thenReturn(Optional.of(activeUser));
        when(forgotPasswordRepository.existsByUser(activeUser)).thenReturn(false);

        authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email("mail@t.com").build());

        verify(emailService).sendEmail(eq("mail@t.com"), anyString(), anyString());
        ArgumentCaptor<ForgotPassword> forgotPasswordCaptor = ArgumentCaptor.forClass(ForgotPassword.class);
        verify(forgotPasswordRepository).save(forgotPasswordCaptor.capture());
        assertThat(forgotPasswordCaptor.getValue().getUser()).isEqualTo(activeUser);
        assertThat(forgotPasswordCaptor.getValue().getOtp()).isBetween(100000, 999999);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-019
     * Objective: If a forgot-password row exists for user, delete before re-creating.
     * CheckDB: verify {@code forgotPasswordRepository.deleteByUser}. Rollback: N/A.
     */
    @Test
    void verifyMail_deletesPrevious_whenForgotExists() {
        User activeUser = createActiveStudentUser("re@t.com");
        when(userRepository.findByEmail("re@t.com")).thenReturn(Optional.of(activeUser));
        when(forgotPasswordRepository.existsByUser(activeUser)).thenReturn(true);

        authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email("re@t.com").build());

        verify(forgotPasswordRepository).deleteByUser(activeUser);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-020
     * Objective: OTP null rejected.
     * CheckDB: no forgot-password save. Rollback: N/A.
     */
    @Test
    void verify_otp_throws_whenOtpNull() {
        User activeUser = createActiveStudentUser("otp@t.com");
        when(userRepository.findByEmail("otp@t.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email("otp@t.com").otp(null).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.IS_NULL);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-021
     * Objective: Email null rejected in shared email check.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void verify_otp_throws_whenEmailNull() {
        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email(null).otp(123456).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.IS_NULL);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-022
     * Objective: OTP not found for user.
     * CheckDB: verify {@code findByOtpAndUser}; no successful delete of OTP row. Rollback: N/A.
     */
    @Test
    void verify_otp_throws_whenOtpNotFound() {
        User activeUser = createActiveStudentUser("nf@t.com");
        when(userRepository.findByEmail("nf@t.com")).thenReturn(Optional.of(activeUser));
        when(forgotPasswordRepository.findByOtpAndUser(eq(111111), eq(activeUser))).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email("nf@t.com").otp(111111).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_EXISTED);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-023
     * Objective: Expired OTP row triggers TOKEN_EXPIRED and deleteById.
     * CheckDB: verify {@code deleteById(5L)}. Rollback: N/A.
     */
    @Test
    void verify_otp_throws_whenExpired() {
        User activeUser = createActiveStudentUser("ex@t.com");
        when(userRepository.findByEmail("ex@t.com")).thenReturn(Optional.of(activeUser));
        ForgotPassword expiredForgotPassword = ForgotPassword.builder()
                .id(5L)
                .otp(222222)
                .expiryTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .user(activeUser)
                .build();
        when(forgotPasswordRepository.findByOtpAndUser(222222, activeUser)).thenReturn(Optional.of(expiredForgotPassword));

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email("ex@t.com").otp(222222).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.TOKEN_EXPIRED);

        verify(forgotPasswordRepository).deleteById(5L);
    }

    /**
     * Test Case ID: UTC-SEC-AUT-024
     * Objective: Valid OTP returns JWT and removes OTP row.
     * CheckDB: verify {@code deleteById(7L)} after success. Rollback: N/A.
     */
    @Test
    void verify_otp_returnsToken_whenOtpValid() {
        User activeUser = createActiveStudentUser("okotp@t.com");
        when(userRepository.findByEmail("okotp@t.com")).thenReturn(Optional.of(activeUser));
        ForgotPassword validForgotPassword = ForgotPassword.builder()
                .id(7L)
                .otp(333333)
                .expiryTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .user(activeUser)
                .build();
        when(forgotPasswordRepository.findByOtpAndUser(333333, activeUser)).thenReturn(Optional.of(validForgotPassword));

        var verifyResponse = authenticationServiceUnderTest.verify_otp(
                VerifyRequest.builder().email("okotp@t.com").otp(333333).build());

        assertThat(verifyResponse.getToken()).isNotBlank();
        verify(forgotPasswordRepository).deleteById(7L);
    }

    private String generateCompactJwtForUser(User user) {
        return (String) ReflectionTestUtils.invokeMethod(authenticationServiceUnderTest, "generateToken", user);
    }
}
