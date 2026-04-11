package com.doan2025.webtoeic.security;

import com.doan2025.webtoeic.dto.request.IntrospectRequest;
import com.doan2025.webtoeic.dto.response.IntrospectResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerJwtDecoder}.
 * <p>
 * <b>Database (CheckDB / Rollback):</b> {@link AuthenticationService#introspect} is mocked.
 * No real database is used. Integration tests with a real DB would require separate test setup.
 */
@ExtendWith(MockitoExtension.class)
class CustomerJwtDecoderTest {

    private static final String HS512_SIGNER_KEY_FOR_TESTS =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private AuthenticationService authenticationService;

    private CustomerJwtDecoder customerJwtDecoderUnderTest;

    @BeforeEach
    void setUpDecoderWithSignerKey() {
        customerJwtDecoderUnderTest = new CustomerJwtDecoder(authenticationService);
        ReflectionTestUtils.setField(customerJwtDecoderUnderTest, "signerKey", HS512_SIGNER_KEY_FOR_TESTS);
    }

    /**
     * Test Case ID: UTC-SEC-CJD-001
     * Objective: When introspection reports valid token, {@link CustomerJwtDecoder#decode} returns a Spring {@link Jwt} with correct subject.
     * CheckDB: N/A (mocked {@link AuthenticationService}). Rollback: N/A.
     */
    @Test
    void decode_returnsSpringJwt_whenIntrospectValid() throws Exception {
        String compactJwt = buildHs512CompactJwtForSubject("user@decode.test");
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        Jwt decodedJwt = customerJwtDecoderUnderTest.decode(compactJwt);

        assertThat(decodedJwt.getSubject()).isEqualTo("user@decode.test");
    }

    /**
     * Test Case ID: UTC-SEC-CJD-002
     * Objective: When introspection returns invalid, decoder throws {@link JwtException} with "Token invalid".
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void decode_throwsJwtException_whenIntrospectInvalid() throws Exception {
        String compactJwt = buildHs512CompactJwtForSubject("any@any.com");
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(false).build());

        assertThatThrownBy(() -> customerJwtDecoderUnderTest.decode(compactJwt))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Token invalid");
    }

    /**
     * Test Case ID: UTC-SEC-CJD-003
     * Objective: Parse errors from introspection are wrapped as {@link JwtException}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void decode_wrapsException_whenIntrospectThrowsParseException() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenThrow(new ParseException("bad token", 0));

        assertThatThrownBy(() -> customerJwtDecoderUnderTest.decode("x"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("bad token");
    }

    private static String buildHs512CompactJwtForSubject(String subject) {
        SecretKey signingKey = Keys.hmacShaKeyFor(HS512_SIGNER_KEY_FOR_TESTS.getBytes());
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 120_000))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }
}
