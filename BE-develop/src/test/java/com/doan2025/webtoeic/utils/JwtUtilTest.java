package com.doan2025.webtoeic.utils;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.constants.enums.ResponseObject;
import com.doan2025.webtoeic.exception.WebToeicException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtUtil}.
 * <p>
 * <b>Database (CheckDB / Rollback):</b> This class does not access a real database.
 * There is no persistence layer to verify or roll back. Any future test that uses a real
 * datasource should use {@code @Transactional} on the test class/method (or Testcontainers)
 * and assert rows explicitly for CheckDB, relying on rollback after the test.
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    /** HS512 raw signer key (64 ASCII bytes) aligned with production usage of {@code Keys.hmacShaKeyFor(key.getBytes())}. */
    private static final String RAW_SIGNER_KEY_FOR_TESTS =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private HttpServletRequest httpServletRequest;

    private JwtUtil jwtUtilUnderTest;

    @BeforeEach
    void setUpJwtUtilWithSignerKey() {
        jwtUtilUnderTest = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtilUnderTest, "SIGNER_KEY", RAW_SIGNER_KEY_FOR_TESTS);
    }

    /**
     * Test Case ID: UTC-SEC-JU-001
     * Objective: {@link JwtUtil#getJwtFromRequest(HttpServletRequest)} extracts the JWT when Authorization is a valid Bearer token.
     * CheckDB: N/A (no database).
     * Rollback: N/A.
     */
    @Test
    void getJwtFromRequest_returnsToken_whenBearerPresent() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer abc.def.ghi");
        assertThat(jwtUtilUnderTest.getJwtFromRequest(httpServletRequest)).isEqualTo("abc.def.ghi");
    }

    /**
     * Test Case ID: UTC-SEC-JU-002
     * Objective: Missing Authorization header throws {@link WebToeicException} with CANNOT_GET / TOKEN.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getJwtFromRequest_throws_whenHeaderMissing() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn(null);
        assertThatThrownBy(() -> jwtUtilUnderTest.getJwtFromRequest(httpServletRequest))
                .isInstanceOf(WebToeicException.class)
                .satisfies(ex -> {
                    WebToeicException webToeicException = (WebToeicException) ex;
                    assertThat(webToeicException.getResponseCode()).isEqualTo(ResponseCode.CANNOT_GET);
                    assertThat(webToeicException.getResponseObject()).isEqualTo(ResponseObject.TOKEN);
                });
    }

    /**
     * Test Case ID: UTC-SEC-JU-003
     * Objective: Non-Bearer Authorization header is rejected with {@link WebToeicException}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getJwtFromRequest_throws_whenHeaderNotBearer() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Basic xxx");
        assertThatThrownBy(() -> jwtUtilUnderTest.getJwtFromRequest(httpServletRequest))
                .isInstanceOf(WebToeicException.class);
    }

    /**
     * Test Case ID: UTC-SEC-JU-004
     * Objective: Document current behaviour when header is exactly {@code "Bearer "} (empty token segment).
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getJwtFromRequest_returnsEmpty_whenBearerHasNoTokenPart() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer ");
        assertThat(jwtUtilUnderTest.getJwtFromRequest(httpServletRequest)).isEmpty();
    }

    /**
     * Test Case ID: UTC-SEC-JU-005
     * Objective: {@link JwtUtil#getEmailFromToken(HttpServletRequest)} returns JWT subject for a valid token.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getEmailFromToken_returnsSubject_whenTokenValid() {
        String expectedEmail = "user@test.com";
        String compactJwt = buildSignedJwtWithRawSignerKey(expectedEmail);
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + compactJwt);
        assertThat(jwtUtilUnderTest.getEmailFromToken(httpServletRequest)).isEqualTo(expectedEmail);
    }

    /**
     * Test Case ID: UTC-SEC-JU-006
     * Objective: Malformed JWT in Authorization header yields {@link IllegalArgumentException}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getEmailFromToken_throwsIllegalArgument_whenJwtMalformed() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer not-a-jwt");
        assertThatThrownBy(() -> jwtUtilUnderTest.getEmailFromToken(httpServletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT token");
    }

    /**
     * Test Case ID: UTC-SEC-JU-007
     * Objective: {@link JwtUtil#getEmailFromTokenString(String)} returns subject for a valid compact JWT.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getEmailFromTokenString_returnsSubject_whenTokenValid() {
        String expectedEmail = "a@b.com";
        String compactJwt = buildSignedJwtWithRawSignerKey(expectedEmail);
        assertThat(jwtUtilUnderTest.getEmailFromTokenString(compactJwt)).isEqualTo(expectedEmail);
    }

    /**
     * Test Case ID: UTC-SEC-JU-008
     * Objective: Invalid compact JWT string throws {@link IllegalArgumentException}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getEmailFromTokenString_throws_whenInvalid() {
        assertThatThrownBy(() -> jwtUtilUnderTest.getEmailFromTokenString("x.y.z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test Case ID: UTC-SEC-JU-009
     * Objective: {@link JwtUtil#getSigningKey()} fails fast when {@code jwt.signerKey} is null.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getSigningKey_throws_whenSignerKeyNull() {
        ReflectionTestUtils.setField(jwtUtilUnderTest, "SIGNER_KEY", null);
        assertThatThrownBy(() -> jwtUtilUnderTest.getSigningKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signerKey is null");
    }

    /**
     * Test Case ID: UTC-SEC-JU-010
     * Objective: Valid Base64-encoded key material produces a non-null HmacSHA512 {@link SecretKey}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getSigningKey_returnsKey_whenValidBase64() {
        byte[] rawKeyBytes = new byte[64];
        for (int index = 0; index < 64; index++) {
            rawKeyBytes[index] = (byte) index;
        }
        String base64SignerKey = Base64.getEncoder().encodeToString(rawKeyBytes);
        ReflectionTestUtils.setField(jwtUtilUnderTest, "SIGNER_KEY", base64SignerKey);
        SecretKey signingKey = jwtUtilUnderTest.getSigningKey();
        assertThat(signingKey).isNotNull();
        assertThat(signingKey.getAlgorithm()).isEqualTo("HmacSHA512");
    }

    /**
     * Test Case ID: UTC-SEC-JU-011
     * Objective: Invalid Base64 in {@code jwt.signerKey} is rejected by the JWT decoder.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void getSigningKey_throws_whenBase64Invalid() {
        ReflectionTestUtils.setField(jwtUtilUnderTest, "SIGNER_KEY", "@@@not-valid-base64@@@");
        assertThatThrownBy(() -> jwtUtilUnderTest.getSigningKey())
                .isInstanceOf(io.jsonwebtoken.io.DecodingException.class);
    }

    private static String buildSignedJwtWithRawSignerKey(String subjectClaim) {
        SecretKey signingKey = Keys.hmacShaKeyFor(RAW_SIGNER_KEY_FOR_TESTS.getBytes());
        return Jwts.builder()
                .setSubject(subjectClaim)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }
}
