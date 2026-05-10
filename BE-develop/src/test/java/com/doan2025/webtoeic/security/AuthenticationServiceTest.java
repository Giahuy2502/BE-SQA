package com.doan2025.webtoeic.security;

import com.doan2025.webtoeic.constants.Constants;
import com.doan2025.webtoeic.constants.enums.ERole;
import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.domain.Consultant;
import com.doan2025.webtoeic.domain.ForgotPassword;
import com.doan2025.webtoeic.domain.InvalidatedToken;
import com.doan2025.webtoeic.domain.Manager;
import com.doan2025.webtoeic.domain.Student;
import com.doan2025.webtoeic.domain.Teacher;
import com.doan2025.webtoeic.domain.User;
import com.doan2025.webtoeic.dto.request.AuthenticationRequest;
import com.doan2025.webtoeic.dto.request.IntrospectRequest;
import com.doan2025.webtoeic.dto.request.RegisterRequest;
import com.doan2025.webtoeic.dto.request.VerifyRequest;
import com.doan2025.webtoeic.dto.response.AuthenticationResponse;
import com.doan2025.webtoeic.exception.WebToeicException;
import com.doan2025.webtoeic.repository.*;
import com.doan2025.webtoeic.service.EmailService;
import com.doan2025.webtoeic.utils.JwtUtil;
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

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticationService} (JUnit + Mockito).
 *
 * Naming convention: MethodName_StateUnderTest_ExpectedBehavior
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
    void setUp_DefaultJwtConfiguration() {
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "SIGNER_KEY", SIGNER_KEY_FOR_TESTS);
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "VALID_DURATION", 3600L);
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "REFRESHABLE_DURATION", 7200L);
        ReflectionTestUtils.setField(authenticationServiceUnderTest, "ISSUER", "test-issuer");
    }

    private User newUser(String email, String encodedPassword, ERole role, boolean isActive, boolean isDelete) {
        User user = new User(email, encodedPassword, "First", "Last", role);
        user.setId(10L);
        user.setIsActive(isActive);
        user.setIsDelete(isDelete);
        user.setCode("STU000000001");
        return user;
    }

    /**
     * TC-AUTH-001: Kiểm tra introspect token hợp lệ, chưa hết hạn, chưa bị vô hiệu hóa
     * Mục đích: Xác minh token hợp lệ trả về valid = true
     * Dữ liệu đầu vào: Token hợp lệ (chưa hết hạn, chưa bị vô hiệu hóa)
     * Kết quả kỳ vọng: IntrospectResponse.valid = true
     */
    @Test
    void introspect_TokenValidNotExpiredNotInvalidated_ShouldReturnValidTrue() throws Exception {
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        User activeUser = newUser("ok@t.com", "encoded", ERole.STUDENT, true, false);
        String compactJwt = generateCompactJwtForUser(activeUser);

        var introspectResponse = authenticationServiceUnderTest.introspect(
                IntrospectRequest.builder().token(compactJwt).build());

        assertThat(introspectResponse.isValid()).isTrue();
    }

    /**
     * TC-AUTH-002: Kiểm tra introspect token hết hạn
     * Mục đích: Xác minh token hết hạn trả về valid = false
     * Dữ liệu đầu vào: Token đã hết hạn (expiration < now)
     * Kết quả kỳ vọng: IntrospectResponse.valid = false
     */
    @Test
    void introspect_TokenExpired_ShouldReturnValidFalse() throws Exception {
        String expiredCompactJwt = buildJwt("x@x.com", "ROLE_STUDENT", Instant.now().minusSeconds(60), "jit-expired");

        var introspectResponse = authenticationServiceUnderTest.introspect(
                IntrospectRequest.builder().token(expiredCompactJwt).build());

        assertThat(introspectResponse.isValid()).isFalse();
    }

    /**
     * TC-AUTH-003: Kiểm tra introspect token bị vô hiệu hóa (invalidated)
     * Mục đích: Xác minh token trong danh sách đã vô hiệu hóa trả về valid = false
     * Dữ liệu đầu vào: Token có trong InvalidatedTokenRepository
     * Kết quả kỳ vọng: IntrospectResponse.valid = false
     */
    @Test
    void introspect_TokenInvalidated_ShouldReturnValidFalse() throws Exception {
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(true);
        User activeUser = newUser("inv@t.com", "encoded", ERole.STUDENT, true, false);
        String compactJwt = generateCompactJwtForUser(activeUser);

        var introspectResponse = authenticationServiceUnderTest.introspect(
                IntrospectRequest.builder().token(compactJwt).build());

        assertThat(introspectResponse.isValid()).isFalse();
    }

    /**
     * TC-AUTH-004: Kiểm tra introspect token không phải JWT
     * Mục đích: Xác minh token không phải JWT đưa ra ParseException
     * Dữ liệu đầu vào: Token: "not-a-jwt" (không đúng định dạng JWT)
     * Kết quả kỳ vọng: Ném ParseException
     */
    @Test
    void introspect_TokenNotJwt_ShouldPropagateParseException() {
        assertThatThrownBy(() ->
                authenticationServiceUnderTest.introspect(IntrospectRequest.builder().token("not-a-jwt").build()))
                .isInstanceOf(ParseException.class);
    }

    /**
     * TC-AUTH-005: Kiểm tra xác thực người dùng hoạt động, mật khẩu đúng
     * Mục đích: Xác minh xác thực thành công, trả về token và role
     * Dữ liệu đầu vào: Email: login@t.com, password khớp, người dùng hoạt động
     * Kết quả kỳ vọng: AuthenticationResponse.authenticated = true, token không null, role = STUDENT
     */
    @Test
    void authenticate_UserActivePasswordCorrect_ShouldReturnAuthenticationResponseWithTokenAndRole() {
        User activeUser = newUser("login@t.com", "encodedPasswordHash", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("login@t.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "encodedPasswordHash")).thenReturn(true);

        var authenticationResponse = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("login@t.com").password("secret").build());

        assertThat(authenticationResponse.isAuthenticated()).isTrue();
        assertThat(authenticationResponse.getToken()).isNotBlank();
        assertThat(authenticationResponse.getRole()).isEqualTo(ERole.STUDENT.getValue());
    }

    /**
     * TC-AUTH-006: Kiểm tra token tạo có đúng subject và scope claim
     * Mục đích: Xác minh JWT token có subject = email và scope = role chính xác
     * Dữ liệu đầu vào: Email: claims@t.com, password khớp
     * Kết quả kỳ vọng: Token.subject = "claims@t.com", Token.scope = "ROLE_STUDENT"
     */
    @Test
    void authenticate_UserActivePasswordCorrect_ShouldReturnTokenWithSubjectAndScopeClaims() throws Exception {
        User activeUser = newUser("claims@t.com", "encodedPasswordHash", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("claims@t.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("secret", "encodedPasswordHash")).thenReturn(true);

        AuthenticationResponse authenticationResponse = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("claims@t.com").password("secret").build());

        JWTClaimsSet claims = parseClaims(authenticationResponse.getToken());
        assertThat(claims.getSubject()).isEqualTo("claims@t.com");
        assertThat(claims.getStringClaim("scope")).isEqualTo("ROLE_STUDENT");
    }

    /**
     * TC-AUTH-007: Kiểm tra email có khoảng trắng thưa được loại bỏ
     * Mục đích: Xác minh hệ thống trim email trước tìm kiếm
     * Dữ liệu đầu vào: Email: "  trim@t.com  " (có khoảng trắng)
     * Kết quả kỳ vọng: Repository tìm kiếm "trim@t.com" (sau trim)
     */
    @Test
    void authenticate_EmailHasWhitespace_ShouldTrimAndFindUser() {
        User activeUser = newUser("trim@t.com", "encodedPasswordHash", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("trim@t.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), eq("encodedPasswordHash"))).thenReturn(true);

        authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("  trim@t.com  ").password("p").build());

        verify(userRepository).findByEmail("trim@t.com");
    }

    /**
     * TC-AUTH-008: Kiểm tra lỗi email không tồn tại
     * Mục đích: Xác minh xử lý lỗi khi email không có trong database
     * Dữ liệu đầu vào: Email: no@t.com, không có trong repository
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_EXISTED
     */
    @Test
    void authenticate_EmailNotFound_ShouldThrowNotExistedEmail() {
        when(userRepository.findByEmail("no@t.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.authenticate(
                        AuthenticationRequest.builder().email("no@t.com").password("p").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_EXISTED);
    }

    /**
     * TC-AUTH-009: Kiểm tra lỗi mật khẩu sai
     * Mục đích: Xác minh hệ thống kiểm tra mật khẩu chính xác
     * Dữ liệu đầu vào: Email: bad@t.com, password: wrong (không khớp)
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.INVALID_PASSWORD
     */
    @Test
    void authenticate_PasswordIncorrect_ShouldThrowInvalidPassword() {
        User activeUser = newUser("bad@t.com", "encodedPasswordHash", ERole.STUDENT, true, false);
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
     * TC-AUTH-010: Kiểm tra người dùng bị khóa và xoá
     * Mục đích: Xác minh người dùng bị khóa (isActive=false) và xoá (isDelete=true) được chặn
     * Dữ liệu đầu vào: Email: gone@t.com, isActive=false, isDelete=true
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.NOT_AVAILABLE
     */
    @Test
    void authenticate_UserInactiveAndDeleted_ShouldThrowNotAvailableUser() {
        User inactiveDeletedUser = newUser("gone@t.com", "encoded", ERole.STUDENT, false, true);
        when(userRepository.findByEmail("gone@t.com")).thenReturn(Optional.of(inactiveDeletedUser));

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.authenticate(
                        AuthenticationRequest.builder().email("gone@t.com").password("p").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_AVAILABLE);
    }

    /**
     * TC-AUTH-011: Kiểm tra đăng ký với vai trò null mặc định là STUDENT
     * Mục đích: Xác minh với vai trò null, hệ thống sế tạo Student entity
     * Dữ liệu đầu vào: RegisterRequest với role = null
     * Kết quả kỳ vọng: StudentRepository.save() được gọi
     */
    @Test
    void register_RoleNull_ShouldDefaultToStudentAndSaveStudentEntity() {
        when(userRepository.existsByEmail("new@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(99L);
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
     * TC-AUTH-012: Kiểm tra lỗi email đã tồn tại khi đăng ký
     * Mục đích: Xác minh ngăn chặn đăng ký trùng email
     * Dữ liệu đầu vào: Email: dup@t.com (already exists in repository)
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.EXISTED
     */
    @Test
    void register_EmailAlreadyExists_ShouldThrowExistedEmail() {
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
     * TC-AUTH-013: Kiểm tra lỗi vai trò không hợp lệ khi đăng ký
     * Mục đích: Xác minh hệ thống chỉ chấp vai trò hợp lệ
     * Dữ liệu đầu vào: RegisterRequest với role = 999 (invalid)
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.INVALID
     */
    @Test
    void register_RoleInvalid_ShouldThrowInvalidRole() {
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
     * TC-AUTH-014: Kiểm tra đăng ký với vai trò MANAGER
     * Mục đích: Xác minh với vai trò MANAGER, hệ thống sế tạo Manager entity
     * Dữ liệu đầu vào: RegisterRequest với role = 1 (MANAGER)
     * Kết quả kỳ vọng: ManagerRepository.save() được gọi
     */
    @Test
    void register_RoleManager_ShouldSaveManagerEntity() {
        when(userRepository.existsByEmail("man@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(1L);
            return userToSave;
        });
        when(managerRepository.save(any(Manager.class))).thenAnswer(inv -> inv.getArgument(0));

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
     * TC-AUTH-015: Kiểm tra đăng xuất thành công
     * Mục đích: Xác minh token được thêm vào danh sách vô hiệu hóa với jit và thời gian hết hạn
     * Dữ liệu đầu vào: Token hợp lệ, chưa hết hạn
     * Kết quả kỳ vọng: InvalidatedTokenRepository.save() được gọi với token và expiryTime
     */
    @Test
    void logout_TokenValid_ShouldSaveInvalidatedTokenWithJitAndExpiry() throws Exception {
        String compactJwt = buildJwt("out@t.com", "ROLE_STUDENT", Instant.now().plusSeconds(120), "jit-logout");
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(compactJwt);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);

        authenticationServiceUnderTest.logout(httpServletRequest);

        ArgumentCaptor<InvalidatedToken> captor = ArgumentCaptor.forClass(InvalidatedToken.class);
        verify(invalidatedTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("jit-logout");
        assertThat(captor.getValue().getExpiryTime()).isNotNull();
    }

    /**
     * TC-AUTH-016: Kiểm tra đăng xuất với token đã bị vô hiệu hóa
     * Mục đích: Xác minh logout với token đã vô hiệu hóa không lưu lại
     * Dữ liệu đầu vào: Token đã có trong InvalidatedTokenRepository
     * Kết quả kỳ vọng: InvalidatedTokenRepository.save() không được gọi
     */
    @Test
    void logout_TokenInvalidated_ShouldSwallowWebToeicExceptionAndNotSave() throws Exception {
        String token = buildJwt("x@x.com", "ROLE_STUDENT", Instant.now().plusSeconds(120), "jit-inv");
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(token);
        when(invalidatedTokenRepository.existsByToken("jit-inv")).thenReturn(true);

        authenticationServiceUnderTest.logout(httpServletRequest);

        verify(invalidatedTokenRepository, never()).save(any());
    }

    /**
     * TC-AUTH-017: Kiểm tra refresh token thành công
     * Mục đích: Xác minh token cu cũ được vô hiệu hóa và token mới được tạo
     * Dữ liệu đầu vào: Token hợp lệ, người dùng tồn tại và hoạt động
     * Kết quả kỳ vọng: Token mới được trả về, authenticated = true
     */
    @Test
    void refreshToken_TokenValidUserExists_ShouldInvalidateOldTokenAndReturnNewToken() throws Exception {
        String compactJwt = buildJwt("ref@t.com", "ROLE_STUDENT", Instant.now().plusSeconds(120), "jit-refresh");
        User activeUser = newUser("ref@t.com", "encoded", ERole.STUDENT, true, false);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(httpServletRequest)).thenReturn(compactJwt);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByEmail("ref@t.com")).thenReturn(Optional.of(activeUser));

        var authenticationResponse = authenticationServiceUnderTest.refreshToken(httpServletRequest);

        assertThat(authenticationResponse.getToken()).isNotBlank();
        assertThat(authenticationResponse.isAuthenticated()).isTrue();
        ArgumentCaptor<InvalidatedToken> captor = ArgumentCaptor.forClass(InvalidatedToken.class);
        verify(invalidatedTokenRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("jit-refresh");
    }

    /**
     * TC-AUTH-018: Kiểm tra refresh token với email không tồn tại
     * Mục đích: Xác minh xử lý khi email từ token không có trong database
     * Dữ liệu đầu vào: Token có email: missing@t.com, không có trong repository
     * Kết quả kỳ vọng: Đưa ra WebToeicException với ResponseCode.UNAUTHENTICATED
     */
    @Test
    void refreshToken_EmailNotFound_ShouldThrowUnauthenticatedEmail() throws Exception {
        String compactJwt = buildJwt("missing@t.com", "ROLE_STUDENT", Instant.now().plusSeconds(120), "jit-missing");
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
     * TC-AUTH-019: Kiem tra gui email xac minh
     * Muc dich: Xac minh email duoc gui va ForgotPassword record duoc tao
     * Du lieu dau vao: Email: mail@t.com, nguoi dung ton tai, khong co ForgotPassword cu
     * Ket qua ky vong: EmailService.sendEmail() va ForgotPasswordRepository.save() duoc goi
     */
    @Test
    void verifyMail_EmailValidNoExistingForgotPassword_ShouldSendEmailAndSaveForgotPassword() {
        User activeUser = newUser("mail@t.com", "encoded", ERole.STUDENT, true, false);
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
     * TC-AUTH-020: Kiem tra ForgotPassword da ton tai khi verify email
     * Muc dich: Neu ForgotPassword da co, xoa cu roi luu moi
     * Du lieu dau vao: Email: re@t.com, nguoi dung ton tai, ForgotPassword da ton tai
     * Ket qua ky vong: ForgotPasswordRepository.deleteByUser() duoc goi
     */
    @Test
    void verifyMail_ForgotPasswordExists_ShouldDeleteByUserBeforeSavingNew() {
        User activeUser = newUser("re@t.com", "encoded", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("re@t.com")).thenReturn(Optional.of(activeUser));
        when(forgotPasswordRepository.existsByUser(activeUser)).thenReturn(true);

        authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email("re@t.com").build());

        verify(forgotPasswordRepository).deleteByUser(activeUser);
    }

    /**
     * TC-AUTH-021: Kiem tra loi OTP null
     * Muc dich: Xac minh OTP bat buoc khong duoc null
     * Du lieu dau vao: VerifyRequest voi email: otp@t.com, otp: null
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.IS_NULL
     */
    @Test
    void verify_otp_OtpNull_ShouldThrowIsNullCode() {
        User activeUser = newUser("otp@t.com", "encoded", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("otp@t.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email("otp@t.com").otp(null).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.IS_NULL);
    }

    /**
     * TC-AUTH-022: Kiem tra loi email null
     * Muc dich: Xac minh email bat buoc khong duoc null
     * Du lieu dau vao: VerifyRequest voi email: null, otp: 123456
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.IS_NULL
     */
    @Test
    void verify_otp_EmailNull_ShouldThrowIsNullEmail() {
        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email(null).otp(123456).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.IS_NULL);
    }

    /**
     * TC-AUTH-023: Kiem tra loi OTP khong ton tai
     * Muc dich: Xac minh xu ly khi OTP khong co trong database
     * Du lieu dau vao: Email: nf@t.com, OTP: 111111, khong co trong repository
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.NOT_EXISTED
     */
    @Test
    void verify_otp_OtpNotFound_ShouldThrowNotExistedCode() {
        User activeUser = newUser("nf@t.com", "encoded", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("nf@t.com")).thenReturn(Optional.of(activeUser));
        when(forgotPasswordRepository.findByOtpAndUser(eq(111111), eq(activeUser))).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authenticationServiceUnderTest.verify_otp(VerifyRequest.builder().email("nf@t.com").otp(111111).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_EXISTED);
    }

    /**
     * TC-AUTH-024: Kiem tra loi OTP het han
     * Muc dich: Xac minh OTP het han bi xoa va dua ra loi
     * Du lieu dau vao: Email: ex@t.com, OTP: 222222 (het han 1 gio)
     * Ket qua ky vong: ForgotPasswordRepository.deleteById() duoc goi, Dua ra WebToeicException.TOKEN_EXPIRED
     */
    @Test
    void verify_otp_OtpExpired_ShouldDeleteByIdAndThrowTokenExpired() {
        User activeUser = newUser("ex@t.com", "encoded", ERole.STUDENT, true, false);
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
     * TC-AUTH-025: Kiem tra OTP hop le duoc xac minh
     * Muc dich: Xac minh OTP hop le duoc xoa va token moi duoc tao
     * Du lieu dau vao: Email: okotp@t.com, OTP: 333333 (con lai 5 phut)
     * Ket qua ky vong: Token duoc tra ve, ForgotPasswordRepository.deleteById() duoc goi
     */
    @Test
    void verify_otp_OtpValid_ShouldDeleteByIdAndReturnVerifyResponseWithToken() {
        User activeUser = newUser("okotp@t.com", "encoded", ERole.STUDENT, true, false);
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

    /**
     * TC-AUTH-026: Kiem tra token OTP co dung subject va scope claim
     * Muc dich: Xac minh JWT token tu OTP co subject = email va scope = role
     * Du lieu dau vao: Email: otpclaims@t.com, vai tro TEACHER, OTP: 888888 hop le
     * Ket qua ky vong: Token.subject = "otpclaims@t.com", Token.scope = "ROLE_TEACHER"
     */
    @Test
    void verify_otp_OtpValid_ShouldReturnTokenWithSubjectAndScopeClaims() throws Exception {
        User teacherUser = newUser("otpclaims@t.com", "encoded", ERole.TEACHER, true, false);
        when(userRepository.findByEmail("otpclaims@t.com")).thenReturn(Optional.of(teacherUser));
        ForgotPassword validForgotPassword = ForgotPassword.builder()
                .id(77L)
                .otp(888888)
                .expiryTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .user(teacherUser)
                .build();
        when(forgotPasswordRepository.findByOtpAndUser(888888, teacherUser)).thenReturn(Optional.of(validForgotPassword));

        var verifyResponse = authenticationServiceUnderTest.verify_otp(
                VerifyRequest.builder().email("otpclaims@t.com").otp(888888).build());

        JWTClaimsSet claims = parseClaims(verifyResponse.getToken());
        assertThat(claims.getSubject()).isEqualTo("otpclaims@t.com");
        assertThat(claims.getStringClaim("scope")).isEqualTo("ROLE_TEACHER");
    }

    /**
     * TC-AUTH-027: Kiem tra xac thuc voi isActive=true, isDelete=false
     * Muc dich: Xac minh che do: chi chan khi BOTH inactive AND deleted
     * Du lieu dau vao: Email: a@t.com, isActive=true, isDelete=false
     * Ket qua ky vong: Xac thuc thanh cong, authenticated = true
     */
    @Test
    void authenticate_IsActiveTrueIsDeleteFalse_ShouldNotBeBlocked() {
        User user = newUser("a@t.com", "encoded", ERole.STUDENT, true, false);
        when(userRepository.findByEmail("a@t.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthenticationResponse response = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("a@t.com").password("p").build());

        assertThat(response.isAuthenticated()).isTrue();
    }

    /**
     * TC-AUTH-028: Kiem tra xac thuc voi isActive=false, isDelete=false
     * Muc dich: Xac minh tai khoan bi khos (khong xoa) van duoc phep dang nhap
     * Du lieu dau vao: Email: b@t.com, isActive=false, isDelete=false
     * Ket qua ky vong: Xac thuc thanh cong, authenticated = true
     */
    @Test
    void authenticate_IsActiveFalseIsDeleteFalse_ShouldNotBeBlocked() {
        User user = newUser("b@t.com", "encoded", ERole.STUDENT, false, false);
        when(userRepository.findByEmail("b@t.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthenticationResponse response = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("b@t.com").password("p").build());

        assertThat(response.isAuthenticated()).isTrue();
    }

    /**
     * TC-AUTH-029: Kiem tra xac thuc voi isActive=true, isDelete=true
     * Muc dich: Xac minh tai khoan bi xoa (nhung khong khoas) van duoc phep dang nhap
     * Du lieu dau vao: Email: c@t.com, isActive=true, isDelete=true
     * Ket qua ky vong: Xac thuc thanh cong, authenticated = true
     */
    @Test
    void authenticate_IsActiveTrueIsDeleteTrue_ShouldNotBeBlocked() {
        User user = newUser("c@t.com", "encoded", ERole.STUDENT, true, true);
        when(userRepository.findByEmail("c@t.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthenticationResponse response = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder().email("c@t.com").password("p").build());

        assertThat(response.isAuthenticated()).isTrue();
    }

    /**
     * TC-AUTH-030: Kiem tra dang xuat voi token het han
     * Muc dich: Xac minh logout voi token het han khong luu lai
     * Du lieu dau vao: Token het han (iat + REFRESHABLE_DURATION)
     * Ket qua ky vong: InvalidatedTokenRepository.save() khong duoc goi
     */
    @Test
    void logout_TokenExpired_ShouldSwallowWebToeicExceptionAndNotSave() throws Exception {
        // logout() uses verifyToken(token, true) => expiryTime = iat + REFRESHABLE_DURATION
        Instant issuedAtFarPast = Instant.now().minusSeconds(7200L + 10);
        String token = buildJwtWithIssuedAt("x@x.com", "ROLE_STUDENT",
                issuedAtFarPast,
                Instant.now().plusSeconds(60),
                "jit-exp-logout");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(request)).thenReturn(token);

        authenticationServiceUnderTest.logout(request);

        verify(invalidatedTokenRepository, never()).save(any());
    }

    /**
     * TC-AUTH-031: Kiem tra dang xuat voi token khong phai JWT
     * Muc dich: Xac minh token khong hop le dua ra ParseException
     * Du lieu dau vao: Token: "not.a.jwt" (khong dung dinh dang JWT)
     * Ket qua ky vong: Nem ParseException
     */
    @Test
    void logout_TokenNotJwt_ShouldPropagateParseException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(request)).thenReturn("not.a.jwt");

        assertThatThrownBy(() -> authenticationServiceUnderTest.logout(request))
                .isInstanceOf(ParseException.class);
    }

    /**
     * TC-AUTH-032: Kiem tra refresh token voi signature khong dung
     * Muc dich: Xac minh token ky voi key khac dua ra WebToeicException
     * Du lieu dau vao: Token ky voi key khac
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.INVALID_TOKEN
     */
    @Test
    void refreshToken_TokenInvalid_ShouldPropagateWebToeicException() {
        // Wrong signature => verifyToken throws WebToeicException(INVALID_TOKEN)
        String tokenSignedByOtherKey = buildJwtWithDifferentKey(
                "x@x.com",
                "ROLE_STUDENT",
                Instant.now().plusSeconds(120),
                "jit-bad",
                "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(request)).thenReturn(tokenSignedByOtherKey);

        assertThatThrownBy(() -> authenticationServiceUnderTest.refreshToken(request))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID_TOKEN);
    }

    /**
     * TC-AUTH-033: Kiem tra refresh token voi nguoi dung khoas va bi xoa
     * Muc dich: Xac minh tai khoan bi khoas va xoa khong the refresh token
     * Du lieu dau vao: Token voi email: na@t.com, isActive=false, isDelete=true
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.NOT_AVAILABLE
     */
    @Test
    void refreshToken_UserInactiveAndDeleted_ShouldThrowNotAvailableUser() {
        String token = buildJwt("na@t.com", "ROLE_STUDENT", Instant.now().plusSeconds(120), "jit-na");
        User user = newUser("na@t.com", "encoded", ERole.STUDENT, false, true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(request)).thenReturn(token);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByEmail("na@t.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationServiceUnderTest.refreshToken(request))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_AVAILABLE);
    }

    /**
     * TC-AUTH-034: Kiem tra refresh token voi tai khoan bi khoas nhung chua bi xoa
     * Muc dich: Xac minh tai khoan chi bi khoas (khong xoa) van duoc phep refresh token
     * Du lieu dau vao: Token voi email: inactive@t.com, isActive=false, isDelete=false
     * Ket qua ky vong: Token moi duoc tra ve, authenticated = true
     */
    @Test
    void refreshToken_UserInactiveButNotDeleted_ShouldNotBeBlockedAndReturnNewToken() throws Exception {
        String token = buildJwt("inactive@t.com", "ROLE_STUDENT", Instant.now().plusSeconds(120), "jit-inactive");
        User user = newUser("inactive@t.com", "encoded", ERole.STUDENT, false, false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jwtUtil.getJwtFromRequest(request)).thenReturn(token);
        when(invalidatedTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByEmail("inactive@t.com")).thenReturn(Optional.of(user));

        AuthenticationResponse response = authenticationServiceUnderTest.refreshToken(request);

        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getToken()).isNotBlank();
        // ensure old token was blacklisted
        verify(invalidatedTokenRepository, atLeastOnce()).save(any());
    }

    /**
     * TC-AUTH-035: Kiem tra dang ky voi vai tro TEACHER
     * Muc dich: Xac minh voi vai tro TEACHER, he thong se tao Teacher entity
     * Du lieu dau vao: RegisterRequest voi role = ERole.TEACHER.getValue() (3)
     * Ket qua ky vong: TeacherRepository.save() duoc goi
     */
    @Test
    void register_RoleTeacher_ShouldSaveTeacherEntity() {
        when(userRepository.existsByEmail("t@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("t@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.TEACHER.getValue())
                .build());

        verify(teacherRepository).save(any());
    }

    /**
     * TC-AUTH-036: Kiem tra dang ky voi vai tro CONSULTANT
     * Muc dich: Xac minh voi vai tro CONSULTANT, he thong se tao Consultant entity
     * Du lieu dau vao: RegisterRequest voi role = ERole.CONSULTANT.getValue() (2)
     * Ket qua ky vong: ConsultantRepository.save() duoc goi
     */
    @Test
    void register_RoleConsultant_ShouldSaveConsultantEntity() {
        when(userRepository.existsByEmail("c@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultantRepository.save(any(Consultant.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("c@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.CONSULTANT.getValue())
                .build());

        verify(consultantRepository).save(any());
    }

    /**
     * TC-AUTH-037: Kiem tra dang ky voi ma code bi tran
     * Muc dich: Xac minh he thong tao ma code moi khi code bi trung
     * Du lieu dau vao: Email: loop@t.com, existsByCode() tra ve true, false lan luot
     * Ket qua ky vong: UserRepository.existsByCode() duoc goi 2 lan
     */
    @Test
    void register_CodeCollisionFirstTry_ShouldLoopUntilUnique() {
        when(userRepository.existsByEmail("loop@t.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.existsByCode(anyString())).thenReturn(true, false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("loop@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.STUDENT.getValue())
                .build());

        verify(userRepository, times(2)).existsByCode(anyString());
    }

    /**
     * TC-AUTH-038: Kiem tra dang ky voi ma code doc nhat lan dau
     * Muc dich: Xac minh he thong chi kiem tra code mot lan khi khong co tran
     * Du lieu dau vao: Email: once@t.com, existsByCode() tra ve false
     * Ket qua ky vong: UserRepository.existsByCode() duoc goi 1 lan
     */
    @Test
    void register_CodeUniqueFirstTry_ShouldCheckExistsByCodeOnce() {
        when(userRepository.existsByEmail("once@t.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("once@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.STUDENT.getValue())
                .build());

        verify(userRepository, times(1)).existsByCode(anyString());
    }

    /**
     * TC-AUTH-039: Kiem tra mat khau duoc ma hoa truoc khi luu
     * Muc dich: Xac minh mat khau duoc encode bang passwordEncoder
     * Du lieu dau vao: RegisterRequest voi password = "password12"
     * Ket qua ky vong: passwordEncoder.encode("password12") duoc goi, User.password = "ENCODED"
     */
    @Test
    void register_DefaultState_ShouldEncodePasswordBeforeSaving() {
        when(userRepository.existsByEmail("pw@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password12")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("pw@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(null)
                .build());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("ENCODED");
    }

    /**
     * TC-AUTH-040: Kiem tra dang ky voi vai tro TEACHER tao code voi prefix dung
     * Muc dich: Xac minh teacher code bat dau voi PRE_CODE_TEACHER
     * Du lieu dau vao: RegisterRequest voi vai tro TEACHER
     * Ket qua ky vong: User.code bat dau voi Constants.PRE_CODE_TEACHER
     */
    @Test
    void register_RoleTeacher_ShouldGenerateCodeWithTeacherPrefix() {
        when(userRepository.existsByEmail("pre@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("pre@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.TEACHER.getValue())
                .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCode()).startsWith(Constants.PRE_CODE_TEACHER);
    }

    /**
     * TC-AUTH-041: Kiem tra dang ky voi vai tro CONSULTANT tao code voi prefix dung
     * Muc dich: Xac minh consultant code bat dau voi PRE_CODE_CONSULTANT
     * Du lieu dau vao: RegisterRequest voi vai tro CONSULTANT
     * Ket qua ky vong: User.code bat dau voi Constants.PRE_CODE_CONSULTANT
     */
    @Test
    void register_RoleConsultant_ShouldGenerateCodeWithConsultantPrefix() {
        when(userRepository.existsByEmail("precon@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consultantRepository.save(any(Consultant.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("precon@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.CONSULTANT.getValue())
                .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCode()).startsWith(Constants.PRE_CODE_CONSULTANT);
    }

    /**
     * TC-AUTH-042: Kiem tra dang ky voi vai tro MANAGER tao code voi prefix dung
     * Muc dich: Xac minh manager code bat dau voi PRE_CODE_MANAGER
     * Du lieu dau vao: RegisterRequest voi vai tro MANAGER
     * Ket qua ky vong: User.code bat dau voi Constants.PRE_CODE_MANAGER
     */
    @Test
    void register_RoleManager_ShouldGenerateCodeWithManagerPrefix() {
        when(userRepository.existsByEmail("preman@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(managerRepository.save(any(Manager.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("preman@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.MANAGER.getValue())
                .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCode()).startsWith(Constants.PRE_CODE_MANAGER);
    }

    /**
     * TC-AUTH-043: Kiem tra dang ky voi vai tro STUDENT tao code voi prefix dung
     * Muc dich: Xac minh student code bat dau voi PRE_CODE_STUDENT
     * Du lieu dau vao: RegisterRequest voi vai tro STUDENT
     * Ket qua ky vong: User.code bat dau voi Constants.PRE_CODE_STUDENT
     */
    @Test
    void register_RoleStudent_ShouldGenerateCodeWithStudentPrefix() {
        when(userRepository.existsByEmail("prestu@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("prestu@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.STUDENT.getValue())
                .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCode()).startsWith(Constants.PRE_CODE_STUDENT);
    }

    /**
     * TC-AUTH-COVER-050: Dang ky voi vai tro STUDENT duoc luu vao Student entity (cover else if cuoi)
     * Muc dich: Xac minh dang ky role STUDENT tao va luu Student entity (cover line 198)
     * Du lieu dau vao: RegisterRequest voi role = ERole.STUDENT.getValue() (4)
     * Ket qua ky vong: StudentRepository.save() duoc goi, Student entity duoc tao va luu
     */
    @Test
    void register_RoleStudent_ShouldSaveStudentEntity() {
        when(userRepository.existsByEmail("stu@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User userToSave = inv.getArgument(0);
            userToSave.setId(4L);
            return userToSave;
        });
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("stu@t.com")
                .password("password12")
                .firstName("F")
                .lastName("L")
                .role(ERole.STUDENT.getValue())
                .build());

        verify(studentRepository).save(any(Student.class));
        verify(teacherRepository, never()).save(any());
        verify(consultantRepository, never()).save(any());
        verify(managerRepository, never()).save(any());
    }

    /**
     * TC-AUTH-044: Kiem tra loi email null khi verify mail
     * Muc dich: Xac minh email bat buoc khong duoc null
     * Du lieu dau vao: VerifyRequest voi email = null
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.IS_NULL
     */
    @Test
    void verifyMail_EmailNull_ShouldThrowIsNullEmail() {
        assertThatThrownBy(() -> authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email(null).build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.IS_NULL);
    }

    /**
     * TC-AUTH-045: Kiem tra loi email khong ton tai khi verify mail
     * Muc dich: Xac minh xu ly khi email khong co trong database
     * Du lieu dau vao: Email: x@t.com, khong co trong repository
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.NOT_EXISTED
     */
    @Test
    void verifyMail_EmailNotFound_ShouldThrowNotExistedUser() {
        when(userRepository.findByEmail("x@t.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email("x@t.com").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_EXISTED);
    }

    /**
     * TC-AUTH-046: Kiem tra loi tai khoan bi khoas va xoa khi verify mail
     * Muc dich: Xac minh tai khoan bi khoas va xoa khong the verify mail
     * Du lieu dau vao: Email: na2@t.com, isActive=false, isDelete=true
     * Ket qua ky vong: Dua ra WebToeicException voi ResponseCode.NOT_AVAILABLE
     */
    @Test
    void verifyMail_UserInactiveAndDeleted_ShouldThrowNotAvailableUser() {
        User user = newUser("na2@t.com", "encoded", ERole.STUDENT, false, true);
        when(userRepository.findByEmail("na2@t.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email("na2@t.com").build()))
                .isInstanceOf(WebToeicException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.NOT_AVAILABLE);
    }

    /**
     * TC-AUTH-047: Kiem tra tai khoan bi khoas nhung chua bi xoa se verify mail thanh cong
     * Muc dich: Xac minh tai khoan chi bi khoas van duoc phep gui email xac minh
     * Du lieu dau vao: Email: inactive2@t.com, isActive=false, isDelete=false
     * Ket qua ky vong: EmailService.sendEmail() va ForgotPasswordRepository.save() duoc goi
     */
    @Test
    void verifyMail_UserInactiveButNotDeleted_ShouldProceedAndSendEmail() {
        User user = newUser("inactive2@t.com", "encoded", ERole.STUDENT, false, false);
        when(userRepository.findByEmail("inactive2@t.com")).thenReturn(Optional.of(user));
        when(forgotPasswordRepository.existsByUser(user)).thenReturn(false);

        authenticationServiceUnderTest.verifyMail(VerifyRequest.builder().email("inactive2@t.com").build());

        verify(emailService).sendEmail(eq("inactive2@t.com"), anyString(), anyString());
        verify(forgotPasswordRepository).save(any(ForgotPassword.class));
    }

    /**
     * TC-AUTH-048: Kiem tra OTP voi vai tro null
     * Muc dich: Xac minh khi vai tro null, token van duoc tao
     * Du lieu dau vao: Email: nullrole@t.com, vai tro = null, OTP hop le
     * Ket qua ky vong: Token khong null duoc tra ve
     */
    @Test
    void verify_otp_UserRoleNull_ShouldReturnTokenAndNotThrow() {
        User user = newUser("nullrole@t.com", "encoded", null, true, false);
        when(userRepository.findByEmail("nullrole@t.com")).thenReturn(Optional.of(user));
        ForgotPassword validForgotPassword = ForgotPassword.builder()
                .id(99L)
                .otp(444444)
                .expiryTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .user(user)
                .build();
        when(forgotPasswordRepository.findByOtpAndUser(444444, user)).thenReturn(Optional.of(validForgotPassword));

        var verifyResponse = authenticationServiceUnderTest.verify_otp(
                VerifyRequest.builder().email("nullrole@t.com").otp(444444).build());

        assertThat(verifyResponse.getToken()).isNotBlank();
    }

    /**
     * TC-AUTH-049: Kiem tra token OTP voi vai tro null co scope claim rong
     * Muc dich: Xac minh token co scope claim = "" khi vai tro null
     * Du lieu dau vao: Email: emptyscope@t.com, vai tro = null, OTP: 555555 hop le
     * Ket qua ky vong: Token.scope = "" (rong)
     */
    @Test
    void verify_otp_UserRoleNull_ShouldReturnTokenWithEmptyScopeClaim() throws Exception {
        User user = newUser("emptyscope@t.com", "encoded", null, true, false);
        when(userRepository.findByEmail("emptyscope@t.com")).thenReturn(Optional.of(user));
        ForgotPassword validForgotPassword = ForgotPassword.builder()
                .id(199L)
                .otp(555555)
                .expiryTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .user(user)
                .build();
        when(forgotPasswordRepository.findByOtpAndUser(555555, user)).thenReturn(Optional.of(validForgotPassword));

        var verifyResponse = authenticationServiceUnderTest.verify_otp(
                VerifyRequest.builder().email("emptyscope@t.com").otp(555555).build());

        JWTClaimsSet claims = parseClaims(verifyResponse.getToken());
        assertThat(claims.getSubject()).isEqualTo("emptyscope@t.com");
        assertThat(claims.getStringClaim("scope")).isEqualTo("");
    }

    /**
     * TC-AUTH-051: Kiểm tra register() KHÔNG tự trim firstName có khoảng trắng đầu/cuối
     * Mục đích: Xác minh BUG thực tế – hệ thống lưu " First " thay vì "First" vào database.
     *           Theo yêu cầu nghiệp vụ, firstName phải được trim trước khi lưu.
     *           Expected output phản ánh hành vi ĐÚNG theo business logic (nên là "First"),
     *           nhưng code hiện tại lưu " First " → test này DOCUMENT BUG, expected = BUG VALUE
     * Dữ liệu đầu vào: RegisterRequest với firstName = " First " (có khoảng trắng)
     * Kết quả kỳ vọng (theo business logic đúng): User.firstName được lưu = "First" (đã trim)
     * Kết quả thực tế của code hiện tại: " First " (KHÔNG trim) → BUG tồn tại
     */
    @Test
    void register_FirstNameWithLeadingTrailingSpaces_ShouldSaveTrimmedFirstName() {
        when(userRepository.existsByEmail("trim_fn@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("trim_fn@t.com")
                .password("password12")
                .firstName(" First ")     // firstName có khoảng trắng đầu/cuối
                .lastName("Last")
                .role(null)
                .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getFirstName())
                .as("Business logic: firstName phai duoc trim khoang trang dau/cuoi")
                .isEqualTo("First");
    }

    /**
     * TC-AUTH-052: Kiểm tra register() phải trim lastName có khoảng trắng đầu/cuối
     * Mục đích: Xác minh hệ thống tuân thủ business logic: lastName phải được trim trước khi lưu.
     * Dữ liệu đầu vào: RegisterRequest với lastName = " Last " (có khoảng trắng)
     * Kết quả kỳ vọng: User.lastName = "Last" (đã trim)
     */
    @Test
    void register_LastNameWithLeadingTrailingSpaces_ShouldSaveTrimmedLastName() {
        when(userRepository.existsByEmail("trim_ln@t.com")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email("trim_ln@t.com")
                .password("password12")
                .firstName("First")
                .lastName(" Last ")      // lastName có khoảng trắng đầu/cuối
                .role(null)
                .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        // BUG: code hiện tại lưu " Last " không trim
        // Business logic đúng: lastName phải = "Last" sau khi trim
        assertThat(userCaptor.getValue().getLastName())
                .as("Business logic: lastName phai duoc trim khoang trang dau/cuoi")
                .isEqualTo("Last");
    }

    /**
     * TC-AUTH-053: Kiểm tra register() KHÔNG trim email trước khi kiểm tra existsByEmail
     * Mục đích: Xác minh BUG thực tế – hệ thống gọi existsByEmail(" dup@t.com ") chứ không phải "dup@t.com",
     *           dẫn đến đăng ký trùng lặp email nếu user nhập có khoảng trắng.
     *           Theo business logic đúng, email phải được trim trước kiểm tra.
     * Dữ liệu đầu vào: RegisterRequest với email = " dup@t.com " (có khoảng trắng), email "dup@t.com" đã tồn tại
     * Kết quả kỳ vọng (business logic): Ném WebToeicException EXISTED (vì "dup@t.com" đã tồn tại)
     * Kết quả thực tế (BUG): Không ném exception vì kiểm tra " dup@t.com " (có space) không tìm thấy
     */
    @Test
    void register_EmailWithSpaces_ShouldDetectDuplicateAfterTrim_BUG() {
        // existsByEmail(" dup@t.com ") → false vì code không trim
        // existsByEmail("dup@t.com") → true nếu code có trim
        when(userRepository.existsByEmail(" dup@t.com ")).thenReturn(false);
        when(userRepository.existsByCode(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticationServiceUnderTest.register(RegisterRequest.builder()
                .email(" dup@t.com ")
                .password("password12")
                .firstName("First")
                .lastName("Last")
                .role(null)
                .build());

        verify(userRepository, org.mockito.Mockito.description("Business logic: phai kiem tra email DA TRIM (dup@t.com) chu khong phai email chua trim"))
                .existsByEmail("dup@t.com");
    }

    /**
     * TC-AUTH-054: Kiểm tra authenticate() CÓ trim email đúng theo business logic
     * Mục đích: Xác minh authenticate() thực sự gọi findByEmail("trim@t.com") sau khi trim " trim@t.com "
     *           Đây là hành vi ĐÚNG đã có trong production code (line 94: request.getEmail().trim())
     * Dữ liệu đầu vào: AuthenticationRequest với email = "  trim@t.com  " (có khoảng trắng đầu/cuối)
     * Kết quả kỳ vọng: userRepository.findByEmail("trim@t.com") được gọi (sau trim), không phải "  trim@t.com  "
     */
    @Test
    void authenticate_EmailWithSpaces_ShouldCallFindByEmailWithTrimmedEmail() {
        User user = newUser("trim@t.com", "encoded", ERole.STUDENT, true, false);
        // Stub với email ĐÃ TRIM – nếu code không trim, mock sẽ không match và trả về empty
        when(userRepository.findByEmail("trim@t.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);

        var result = authenticationServiceUnderTest.authenticate(
                AuthenticationRequest.builder()
                        .email("  trim@t.com  ")   // email có khoảng trắng
                        .password("pass")
                        .build()
        );

        // Xác nhận code GỌI findByEmail với "trim@t.com" (đã trim), không phải "  trim@t.com  "
        verify(userRepository).findByEmail("trim@t.com");
        assertThat(result.isAuthenticated()).isTrue();
    }

    /**
     * TC-AUTH-055: Kiểm tra verifyMail() KHÔNG trim email có khoảng trắng → tìm kiếm thất bại
     * Mục đích: Xác minh BUG thực tế – verifyMail() gọi findByEmail(" active@t.com ") với khoảng trắng,
     *           dẫn đến NOT_EXISTED dù email "active@t.com" tồn tại trong DB.
     *           Theo business logic, email phải trim trước tìm kiếm.
     * Dữ liệu đầu vào: VerifyRequest với email = " active@t.com " (có khoảng trắng)
     * Kết quả kỳ vọng (business logic đúng): Tìm thấy user và gửi email thành công
     * Kết quả thực tế (BUG): Ném WebToeicException NOT_EXISTED vì không tìm thấy email có khoảng trắng
     */
    @Test
    void verifyMail_EmailWithSpaces_ShouldSendEmailToTrimmedAddress() {
        // "active@t.com" có trong DB nhưng " active@t.com " (với space) thì không
        when(userRepository.findByEmail(" active@t.com ")).thenReturn(Optional.empty());

        // BUG: verifyMail không trim email → tìm " active@t.com " → NOT_EXISTED
        // Business logic đúng: nên tìm "active@t.com" (trimmed) → tìm thấy và gửi email
        // Business logic dung: nen tim "active@t.com" (trimmed) -> tim thay va gui email
        // Do hien tai code loi khong trim nen se nem exception NOT_EXISTED
        // => Test expect KHONG nem exception (vi gui email thanh cong)
        authenticationServiceUnderTest.verifyMail(
                VerifyRequest.builder().email(" active@t.com ").build()
        );
        verify(emailService).sendEmail(eq(" active@t.com "), anyString(), anyString());
        // TODO: Sau khi fix bug (thêm .trim()), test này phải verify gửi email thành công
    }

    /**
     * TC-AUTH-056: Kiểm tra authenticate() với email null ném WebToeicException
     * Mục đích: Xác minh hệ thống xử lý đúng khi email null được truyền vào authenticate()
     *           (request.getEmail() = null → NullPointerException khi gọi .trim())
     * Dữ liệu đầu vào: AuthenticationRequest với email = null
     * Kết quả kỳ vọng: Ném NullPointerException (do .trim() trên null)
     */
    @Test
    void authenticate_EmailNull_ShouldThrowNullPointerException() {
        // Code line 94: request.getEmail().trim() → NPE khi email null
        assertThatThrownBy(() ->
                authenticationServiceUnderTest.authenticate(
                        AuthenticationRequest.builder()
                                .email(null)
                                .password("pass")
                                .build()
                )
        ).isInstanceOf(NullPointerException.class);
        // TODO: Sau khi fix, nên ném WebToeicException với ResponseCode.IS_NULL thay vì NPE
    }

    private String generateCompactJwtForUser(User user) {
        return (String) ReflectionTestUtils.invokeMethod(authenticationServiceUnderTest, "generateToken", user);
    }

    private String buildJwt(String subject, String scope, Instant exp, String jti) {
        // generateToken() uses jjwt, but verifyToken() uses Nimbus parser + MACVerifier; HS512 is compatible.
        return io.jsonwebtoken.Jwts.builder()
                .subject(subject)
                .issuer("test-issuer")
                .issuedAt(new Date())
                .expiration(Date.from(exp))
                .id(jti)
                .claim("scope", scope)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SIGNER_KEY_FOR_TESTS.getBytes()))
                .compact();
    }

    private String buildJwtWithIssuedAt(String subject, String scope, Instant issuedAt, Instant exp, String jti) {
        return io.jsonwebtoken.Jwts.builder()
                .subject(subject)
                .issuer("test-issuer")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(exp))
                .id(jti)
                .claim("scope", scope)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SIGNER_KEY_FOR_TESTS.getBytes()))
                .compact();
    }

    private String buildJwtWithDifferentKey(String subject, String scope, Instant exp, String jti, String differentKey) {
        return io.jsonwebtoken.Jwts.builder()
                .subject(subject)
                .issuer("test-issuer")
                .issuedAt(new Date())
                .expiration(Date.from(exp))
                .id(jti)
                .claim("scope", scope)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(differentKey.getBytes()))
                .compact();
    }

    private JWTClaimsSet parseClaims(String token) throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(token);
        boolean verified = signedJWT.verify(new MACVerifier(SIGNER_KEY_FOR_TESTS.getBytes()));
        assertThat(verified).isTrue();
        return signedJWT.getJWTClaimsSet();
    }


}
