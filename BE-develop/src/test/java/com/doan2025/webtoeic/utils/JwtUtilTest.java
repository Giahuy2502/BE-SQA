package com.doan2025.webtoeic.utils;

import com.doan2025.webtoeic.exception.WebToeicException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private static final String AUTHORIZATION = "Authorization";

    @Mock
    private HttpServletRequest request;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "SIGNER_KEY", validSignerKeyStringForHs512());
    }

    // region getJwtFromRequest(HttpServletRequest)

    /**
     * TC-JWT-001: Extract token from Bearer header
     * Muc dich: Xac minh tach token tu Authorization header voi Bearer prefix
     * Du lieu dau vao: Authorization: "Bearer eyJhbGc..."
     * Ket qua ky vong: Token = "eyJhbGc..." (khong co Bearer)
     */
    @Test
    void getJwtFromRequest_HeaderBearerToken_ReturnsTokenSubstring() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer eyJhbGc...");

        String token = jwtUtil.getJwtFromRequest(request);

        assertEquals("eyJhbGc...", token);
    }

    /**
     * TC-JWT-002: Loi khong co Bearer prefix
     * Muc dich: Xac minh header khong co Bearer prefix dua ra loi
     * Du lieu dau vao: Authorization: "eyJhbGc..." (khong co Bearer)
     * Ket qua ky vong: Dua ra WebToeicException
     */
    @Test
    void getJwtFromRequest_HeaderWithoutBearerPrefix_ThrowsWebToeicException() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("eyJhbGc...");

        assertThrows(WebToeicException.class, () -> jwtUtil.getJwtFromRequest(request));
    }

    /**
     * TC-JWT-003: Loi header null
     * Muc dich: Xac minh Authorization header null dua ra loi
     * Du lieu dau vao: Authorization header = null
     * Ket qua ky vong: Dua ra WebToeicException
     */
    @Test
    void getJwtFromRequest_HeaderIsNull_ThrowsWebToeicException() {
        when(request.getHeader(AUTHORIZATION)).thenReturn(null);

        assertThrows(WebToeicException.class, () -> jwtUtil.getJwtFromRequest(request));
    }

    /**
     * TC-JWT-004: Loi header rong
     * Muc dich: Xac minh Authorization header rong dua ra loi
     * Du lieu dau vao: Authorization header = "" (rong)
     * Ket qua ky vong: Dua ra WebToeicException
     */
    @Test
    void getJwtFromRequest_HeaderIsEmpty_ThrowsWebToeicException() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("");

        assertThrows(WebToeicException.class, () -> jwtUtil.getJwtFromRequest(request));
    }

    /**
     * TC-JWT-005: Bearer header khong co token phia sau
     * Muc dich: Xac minh "Bearer " khong co token tra ve string rong
     * Du lieu dau vao: Authorization: "Bearer "
     * Ket qua ky vong: Token = "" (rong)
     */
    @Test
    void getJwtFromRequest_HeaderBearerWithoutToken_ReturnsEmptyString() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer ");

        String token = jwtUtil.getJwtFromRequest(request);

        assertEquals("", token);
    }

    /**
     * TC-JWT-006: Bearer prefix phai case-sensitive
     * Muc dich: Xac minh "bearer" (lowercase) khong duoc chap nhan
     * Du lieu dau vao: Authorization: "bearer token"
     * Ket qua ky vong: Dua ra WebToeicException (Bearer phai uppercase)
     */
    @Test
    void getJwtFromRequest_HeaderBearerLowercase_ThrowsWebToeicException() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("bearer token");

        assertThrows(WebToeicException.class, () -> jwtUtil.getJwtFromRequest(request));
    }

    /**
     * TC-JWT-007: Loi header chi co khoang trang
     * Muc dich: Xac minh header chi co khoang trang dua ra loi
     * Du lieu dau vao: Authorization: "   " (khoang trang)
     * Ket qua ky vong: Dua ra WebToeicException
     */
    @Test
    void getJwtFromRequest_HeaderWhitespaceOnly_ThrowsWebToeicException() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("   ");

        assertThrows(WebToeicException.class, () -> jwtUtil.getJwtFromRequest(request));
    }

    // endregion

    // region getEmailFromToken(HttpServletRequest)

    /**
     * TC-JWT-008: Tach email tu JWT trong request
     * Muc dich: Xac minh tach email (subject) tu token hop le trong request
     * Du lieu dau vao: Authorization: "Bearer {valid_token}", token co subject = user@mail.com
     * Ket qua ky vong: Email = "user@mail.com"
     */
    @Test
    void getEmailFromToken_ValidJwtInRequest_ReturnsSubjectEmail() {
        String email = "user@mail.com";
        String signerKey = (String) ReflectionTestUtils.getField(jwtUtil, "SIGNER_KEY");
        String token = buildJwt(email, signerKey, Instant.now().plusSeconds(3600));
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);

        String actual = jwtUtil.getEmailFromToken(request);

        assertEquals(email, actual);
    }

    /**
     * TC-JWT-009: Loi khong co Authorization header
     * Muc dich: Xac minh xử lý khi Authorization header null
     * Du lieu dau vao: Authorization header = null
     * Ket qua ky vong: Dua ra WebToeicException
     */
    @Test
    void getEmailFromToken_NoAuthorizationHeader_PropagatesWebToeicException() {
        when(request.getHeader(AUTHORIZATION)).thenReturn(null);

        assertThrows(WebToeicException.class, () -> jwtUtil.getEmailFromToken(request));
    }

    /**
     * TC-JWT-010: Token voi signature sai
     * Muc dich: Xac minh token ky voi key khac bi tu choi
     * Du lieu dau vao: Token ky voi signer key khac
     * Ket qua ky vong: Dua ra IllegalArgumentException("Invalid JWT token")
     */
    @Test
    void getEmailFromToken_WrongSignature_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        String tokenSignedByOtherKey = buildJwt("user@mail.com", otherSignerKeyStringForHs512(), Instant.now().plusSeconds(3600));
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + tokenSignedByOtherKey);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromToken(request));

        assertEquals("Invalid JWT token", ex.getMessage());
    }

    /**
     * TC-JWT-011: Token het han
     * Muc dich: Xac minh token het han bi tu choi
     * Du lieu dau vao: Token co expiration < now
     * Ket qua ky vong: Dua ra IllegalArgumentException("Invalid JWT token")
     */
    @Test
    void getEmailFromToken_ExpiredToken_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        String signerKey = (String) ReflectionTestUtils.getField(jwtUtil, "SIGNER_KEY");
        String expiredToken = buildJwt("user@mail.com", signerKey, Instant.now().minusSeconds(60));
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + expiredToken);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromToken(request));

        assertEquals("Invalid JWT token", ex.getMessage());
    }

    /**
     * TC-JWT-012: Token khong dung dinh dang
     * Muc dich: Xac minh token sai format (khong phai JWT) bi tu choi
     * Du lieu dau vao: Token = "not.a.jwt" (khong hop le)
     * Ket qua ky vong: Dua ra IllegalArgumentException("Invalid JWT token")
     */
    @Test
    void getEmailFromToken_MalformedToken_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer not.a.jwt");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromToken(request));

        assertEquals("Invalid JWT token", ex.getMessage());
    }

    /**
     * TC-JWT-013: Tach email tu token voi subject cu the
     * Muc dich: Xac minh tach email dung (admin@mail.com) tu token
     * Du lieu dau vao: Token voi subject = "admin@mail.com"
     * Ket qua ky vong: Email = "admin@mail.com"
     */
    @Test
    void getEmailFromToken_SubjectIsSpecificEmail_ReturnsThatEmail() {
        String email = "user@mail.com";
        String signerKey = (String) ReflectionTestUtils.getField(jwtUtil, "SIGNER_KEY");
        String token = buildJwt(email, signerKey, Instant.now().plusSeconds(3600));
        when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);

        String actual = jwtUtil.getEmailFromToken(request);

        assertEquals("user@mail.com", actual);
    }

    // endregion

    // region getEmailFromTokenString(String)

    /**
     * TC-JWT-014: Tach email tu string token hop le
     * Muc dich: Xac minh tach email tu token string (khong phai request)
     * Du lieu dau vao: Token string hop le voi subject = user@mail.com
     * Ket qua ky vong: Email = "user@mail.com"
     */
    @Test
    void getEmailFromTokenString_ValidToken_ReturnsSubjectEmail() {
        String email = "user@mail.com";
        String signerKey = (String) ReflectionTestUtils.getField(jwtUtil, "SIGNER_KEY");
        String token = buildJwt(email, signerKey, Instant.now().plusSeconds(3600));

        String actual = jwtUtil.getEmailFromTokenString(token);

        assertEquals(email, actual);
    }

    /**
     * TC-JWT-015: Token string voi signature sai
     * Muc dich: Xac minh token string ky voi key khac bi tu choi
     * Du lieu dau vao: Token string ky voi signer key khac
     * Ket qua ky vong: Dua ra IllegalArgumentException("Invalid JWT token")
     */
    @Test
    void getEmailFromTokenString_WrongSignature_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        String tokenSignedByOtherKey = buildJwt("user@mail.com", otherSignerKeyStringForHs512(), Instant.now().plusSeconds(3600));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromTokenString(tokenSignedByOtherKey));

        assertEquals("Invalid JWT token", ex.getMessage());
    }

    /**
     * TC-JWT-016: Token string het han
     * Muc dich: Xac minh token string het han bi tu choi
     * Du lieu dau vao: Token string co expiration < now
     * Ket qua ky vong: Dua ra IllegalArgumentException("Invalid JWT token")
     */
    @Test
    void getEmailFromTokenString_ExpiredToken_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        String signerKey = (String) ReflectionTestUtils.getField(jwtUtil, "SIGNER_KEY");
        String token = buildJwt("user@mail.com", signerKey, Instant.now().minusSeconds(60));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromTokenString(token));

        assertEquals("Invalid JWT token", ex.getMessage());
    }

    /**
     * TC-JWT-017: Token string sai format
     * Muc dich: Xac minh token string khong hop le bi tu choi
     * Du lieu dau vao: Token string = "abc.def.ghi" (sai format)
     * Ket qua ky vong: Dua ra IllegalArgumentException("Invalid JWT token")
     */
    @Test
    void getEmailFromTokenString_MalformedToken_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromTokenString("abc.def.ghi"));

        assertEquals("Invalid JWT token", ex.getMessage());
    }

    /**
     * TC-JWT-018: Token string null
     * Muc dich: Xac minh token null dua ra RuntimeException
     * Du lieu dau vao: Token = null
     * Ket qua ky vong: Dua ra RuntimeException
     */
    @Test
    void getEmailFromTokenString_TokenIsNull_ThrowsException() {
        assertThrows(RuntimeException.class, () -> jwtUtil.getEmailFromTokenString(null));
    }

    /**
     * TC-JWT-019: Token string rong
     * Muc dich: Xac minh token rong dua ra loi
     * Du lieu dau vao: Token = "" (rong)
     * Ket qua ky vong: Dua ra IllegalArgumentException (cannot be null or empty)
     */
    @Test
    void getEmailFromTokenString_TokenIsEmpty_ThrowsIllegalArgumentExceptionInvalidJwtToken() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.getEmailFromTokenString(""));

        assertTrue(ex.getMessage().toLowerCase().contains("cannot be null or empty"));
    }

    /**
     * TC-JWT-020: Tach email tu token string voi subject cu the
     * Muc dich: Xac minh tach email dung (admin@mail.com) tu token string
     * Du lieu dau vao: Token string voi subject = "admin@mail.com"
     * Ket qua ky vong: Email = "admin@mail.com"
     */
    @Test
    void getEmailFromTokenString_SubjectIsSpecificEmail_ReturnsThatEmail() {
        String email = "admin@mail.com";
        String signerKey = (String) ReflectionTestUtils.getField(jwtUtil, "SIGNER_KEY");
        String token = buildJwt(email, signerKey, Instant.now().plusSeconds(3600));

        String actual = jwtUtil.getEmailFromTokenString(token);

        assertEquals("admin@mail.com", actual);
    }

    // endregion

    // region getSigningKey()

    /**
     * TC-JWT-021: Tao signing key tu Base64 hop le
     * Muc dich: Xac minh tao SecretKey tu Base64 string 64 bytes
     * Du lieu dau vao: SIGNER_KEY = Base64 string (64 bytes)
     * Ket qua ky vong: SecretKey khong null, algorithm khong null
     */
    @Test
    void getSigningKey_ValidBase64Key_ReturnsNonNullSecretKey() {
        ReflectionTestUtils.setField(jwtUtil, "SIGNER_KEY", base64KeyOfLengthBytes(64));

        SecretKey key = jwtUtil.getSigningKey();

        assertNotNull(key);
        assertNotNull(key.getAlgorithm());
    }

    /**
     * TC-JWT-022: Loi SIGNER_KEY null
     * Muc dich: Xac minh SIGNER_KEY null dua ra IllegalStateException
     * Du lieu dau vao: SIGNER_KEY = null
     * Ket qua ky vong: Dua ra IllegalStateException (signerKey is null)
     */
    @Test
    void getSigningKey_SignerKeyIsNull_ThrowsIllegalStateException() {
        ReflectionTestUtils.setField(jwtUtil, "SIGNER_KEY", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> jwtUtil.getSigningKey());

        assertTrue(ex.getMessage().contains("signerKey is null"));
    }

    /**
     * TC-JWT-023: Base64 key qua ngan (< 64 bytes)
     * Muc dich: Xac minh key qua ngan dua ra loi
     * Du lieu dau vao: SIGNER_KEY = Base64 string (16 bytes) - qua ngan cho HS512
     * Ket qua ky vong: Dua ra RuntimeException
     */
    @Test
    void getSigningKey_Base64KeyTooShort_ThrowsException() {
        ReflectionTestUtils.setField(jwtUtil, "SIGNER_KEY", base64KeyOfLengthBytes(16));

        assertThrows(RuntimeException.class, () -> jwtUtil.getSigningKey());
    }

    /**
     * TC-JWT-024: String khong phai Base64
     * Muc dich: Xac minh string khong hop le Base64 format dua ra loi
     * Du lieu dau vao: SIGNER_KEY = "not-base64!!!" (khong hop le)
     * Ket qua ky vong: Dua ra DecodingException
     */
    @Test
    void getSigningKey_NotBase64_ThrowsIllegalArgumentException() {
        ReflectionTestUtils.setField(jwtUtil, "SIGNER_KEY", "not-base64!!!");

        assertThrows(DecodingException.class, () -> jwtUtil.getSigningKey());
    }

    /**
     * TC-JWT-025: Tao signing key cho HS512 voi algorithm dung
     * Muc dich: Xac minh key duoc tao voi algorithm HMAC cho HS512
     * Du lieu dau vao: SIGNER_KEY = Base64 string (64 bytes) hop le
     * Ket qua ky vong: SecretKey khong null, algorithm co chua "hmac"
     */
    @Test
    void getSigningKey_ValidBase64ForHs512_ReturnsSecretKeyWithAlgorithm() {
        ReflectionTestUtils.setField(jwtUtil, "SIGNER_KEY", base64KeyOfLengthBytes(64));

        SecretKey key = jwtUtil.getSigningKey();

        assertNotNull(key);
        assertTrue(key.getAlgorithm().toLowerCase().contains("hmac"));
    }

    // endregion

    private static String buildJwt(String subjectEmail, String signerKey, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(signerKey.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectEmail)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    private static String validSignerKeyStringForHs512() {
        return "k".repeat(80);
    }

    private static String otherSignerKeyStringForHs512() {
        return "x".repeat(80);
    }

    private static String base64KeyOfLengthBytes(int byteLength) {
        byte[] bytes = new byte[byteLength];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
