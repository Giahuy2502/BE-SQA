package com.doan2025.webtoeic.security;

import com.doan2025.webtoeic.dto.request.IntrospectRequest;
import com.doan2025.webtoeic.dto.response.IntrospectResponse;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerJwtDecoder}.
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
     * TC-DECODER-001: Giải mã JWT hợp lệ với chữ ký HS512 đúng
     * Mục đích: Xác minh giải mã JWT thành công và trả về Jwt object với subject khớp
     * Dữ liệu đầu vào: Token JWT hợp lệ, chữ ký HS512 đúng, introspect trả về valid=true
     * Kết quả kỳ vọng: Trả về Jwt object có subject = "user@decode.test"
     */
    @Test
    void decode_IntrospectValidAndSignatureValid_ShouldReturnJwtWithExpectedSubject() throws Exception {
        String compactJwt = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "user@decode.test",
                Instant.now().plusSeconds(120)
        );
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        Jwt decodedJwt = customerJwtDecoderUnderTest.decode(compactJwt);

        assertEquals("user@decode.test", decodedJwt.getSubject());
    }

    /**
     * TC-DECODER-002: Giải mã JWT khi introspect không hợp lệ
     * Mục đích: Xác minh token không hợp lệ dựa ra JwtException
     * Dữ liệu đầu vào: Token JWT hợp lệ nhưng introspect trả về valid=false
     * Kết quả kỳ vọng: Ném JwtException với message = "Token invalid"
     */
    @Test
    void decode_IntrospectInvalid_ShouldThrowJwtExceptionTokenInvalid() throws Exception {
        String compactJwt = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "any@any.com",
                Instant.now().plusSeconds(120)
        );
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(false).build());

        JwtException ex = assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode(compactJwt));
        assertEquals("Token invalid", ex.getMessage());
    }

    /**
     * TC-DECODER-003: Giải mã JWT khi introspect ném ParseException
     * Mục đích: Xác minh ParseException được bao gói thành JwtException giữ nguyên message
     * Dữ liệu đầu vào: AuthenticationService.introspect() ném ParseException("bad token")
     * Kết quả kỳ vọng: JwtException được ném với message = "bad token"
     */
    @Test
    void decode_IntrospectThrowsParseException_ShouldWrapAsJwtExceptionWithSameMessage() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenThrow(new ParseException("bad token", 0));

        JwtException ex = assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode("x"));
        assertEquals("bad token", ex.getMessage());
    }

    /**
     * TC-DECODER-004: Giải mã JWT khi introspect ném JOSEException
     * Mục đích: Xác minh JOSEException (JSON Web Signature/Encryption) được bao gói thành JwtException
     * Dữ liệu đầu vào: AuthenticationService.introspect() ném JOSEException("jose error")
     * Kết quả kỳ vọng: JwtException được ném với message = "jose error"
     */
    @Test
    void decode_IntrospectThrowsJOSEException_ShouldWrapAsJwtExceptionWithSameMessage() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenThrow(new JOSEException("jose error"));

        JwtException ex = assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode("x"));
        assertEquals("jose error", ex.getMessage());
    }

    /**
     * TC-DECODER-005: Giải mã JWT khi introspect ném exception có message=null
     * Mục đích: Xác minh message=null được giữ nguyên, không bị thay đổi
     * Dữ liệu đầu vào: AuthenticationService.introspect() ném JOSEException(null)
     * Kết quả kỳ vọng: JwtException được ném với message = null
     */
    @Test
    void decode_IntrospectThrowsExceptionWithNullMessage_ShouldThrowJwtExceptionWithNullMessage() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenThrow(new JOSEException((String) null));

        JwtException ex = assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode("x"));
        assertNull(ex.getMessage());
    }

    /**
     * TC-DECODER-006: Giải mã JWT lần đầu tiên khi nimbusJwtDecoder chưa khởi tạo
     * Mục đích: Xác minh lần gọi đầu tiên sẽ khởi tạo Nimbus decoder instance
     * Dữ liệu đầu vào: Field nimbusJwtDecoder = null, token hợp lệ, introspect valid=true
     * Kết quả kỳ vọng: Decoder khởi tạo thành công, trả về Jwt với subject = "first@call.test"
     */
    @Test
    void decode_FirstCallNimbusNullAndIntrospectValid_ShouldInitializeNimbusDecoderAndDecodeSuccessfully() throws Exception {
        ReflectionTestUtils.setField(customerJwtDecoderUnderTest, "nimbusJwtDecoder", null);
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        String compactJwt = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "first@call.test",
                Instant.now().plusSeconds(120)
        );

        Object before = ReflectionTestUtils.getField(customerJwtDecoderUnderTest, "nimbusJwtDecoder");
        assertNull(before);

        Jwt decoded = customerJwtDecoderUnderTest.decode(compactJwt);
        assertEquals("first@call.test", decoded.getSubject());

        Object after = ReflectionTestUtils.getField(customerJwtDecoderUnderTest, "nimbusJwtDecoder");
        assertNotNull(after);
    }

    /**
     * TC-DECODER-007: Giải mã JWT lần thứ hai khi nimbusJwtDecoder đã khởi tạo
     * Mục đích: Xác minh lần gọi thứ hai tái sử dụng instance cũ, không tạo mới
     * Dữ liệu đầu vào: Field nimbusJwtDecoder đã khởi tạo, 2 token hợp lệ liên tiếp
     * Kết quả kỳ vọng: Cùng decoder instance được sử dụng cho cả 2 lần gọi (same object reference)
     */
    @Test
    void decode_SecondCallNimbusAlreadyInitialized_ShouldReuseSameInstance() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        String token1 = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "user1@call.test",
                Instant.now().plusSeconds(120)
        );
        String token2 = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "user2@call.test",
                Instant.now().plusSeconds(120)
        );

        customerJwtDecoderUnderTest.decode(token1);
        Object decoderInstanceAfterFirst = ReflectionTestUtils.getField(customerJwtDecoderUnderTest, "nimbusJwtDecoder");
        assertNotNull(decoderInstanceAfterFirst);

        customerJwtDecoderUnderTest.decode(token2);
        Object decoderInstanceAfterSecond = ReflectionTestUtils.getField(customerJwtDecoderUnderTest, "nimbusJwtDecoder");
        assertSame(decoderInstanceAfterFirst, decoderInstanceAfterSecond);
    }

    /**
     * TC-DECODER-008: Giải mã JWT khi introspect hợp lệ nhưng chữ ký không khớp
     * Mục đích: Xác minh signature verification kiểm tra chặt chẽ
     * Dữ liệu đầu vào: Token ký bởi khóa khác (không khớp SIGNER_KEY), introspect valid=true
     * Kết quả kỳ vọng: JwtException được ném (signature verification failed)
     */
    @Test
    void decode_IntrospectValidButSignatureInvalid_ShouldThrowJwtException() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        String tokenSignedByOtherKey = buildNimbusHs512CompactJwt(
                "other-key-0123456789abcdef0123456789abcdef0123456789abcdef012345",
                "user@bad.signature",
                Instant.now().plusSeconds(120)
        );

        assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode(tokenSignedByOtherKey));
    }

    /**
     * TC-DECODER-009: Giải mã JWT khi token đã hết hạn
     * Mục đích: Xác minh token expiry validation ngăn chặn token hết hạn
     * Dữ liệu đầu vào: Token hợp lệ nhưng hết hạn (expirationTime = now - 5 giây), introspect valid=true
     * Kết quả kỳ vọng: JwtException được ném (token expired)
     */
    @Test
    void decode_IntrospectValidButExpiredToken_ShouldThrowJwtException() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        String expiredToken = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "user@expired.test",
                Instant.now().minusSeconds(5)
        );

        assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode(expiredToken));
    }

    /**
     * TC-DECODER-010: Giải mã JWT khi token=null
     * Mục đích: Xác minh null token xử lý đặc biệt, không ném NullPointerException
     * Dữ liệu đầu vào: Token = null, introspect mock valid=true
     * Kết quả kỳ vọng: RuntimeException được ném (nhưng không phải NullPointerException)
     */
    @Test
    void decode_NullTokenAndIntrospectValid_ShouldThrowNonNullPointerException() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> customerJwtDecoderUnderTest.decode(null));
        assertFalse(ex instanceof NullPointerException);
    }

    /**
     * TC-DECODER-011: Giải mã JWT khi token="" (chuỗi rỗng)
     * Mục đích: Xác minh empty token được từ chối ngay
     * Dữ liệu đầu vào: Token = "" (empty string), introspect mock valid=true
     * Kết quả kỳ vọng: JwtException được ném (invalid token format)
     */
    @Test
    void decode_EmptyTokenAndIntrospectValid_ShouldThrowJwtException() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode(""));
    }

    /**
     * TC-DECODER-012: Giải mã JWT khi token không đúng định dạng JWT
     * Mục đích: Xác minh malformed token (thiếu phần separated by '.') dựa ra JwtException
     * Dữ liệu đầu vào: Token = "not.a.jwt" (không đủ 3 phần ngăn cách), introspect mock valid=true
     * Kết quả kỳ vọng: JwtException được ném (malformed token)
     */
    @Test
    void decode_MalformedTokenAndIntrospectValid_ShouldThrowJwtException() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        assertThrows(JwtException.class, () -> customerJwtDecoderUnderTest.decode("not.a.jwt"));
    }

    /**
     * TC-DECODER-013: Giải mã JWT, kiểm tra token truyền chính xác tới introspect
     * Mục đích: Xác minh hàm decode truyền token đúng tới introspect() không thay đổi
     * Dữ liệu đầu vào: Token JWT hợp lệ với subject="user@arg.test"
     * Kết quả kỳ vọng: introspect() được gọi với token đúng, giải mã trả về subject = "user@arg.test"
     */
    @Test
    void decode_TokenInput_ShouldPassSameTokenToIntrospect() throws Exception {
        String token = buildNimbusHs512CompactJwt(
                HS512_SIGNER_KEY_FOR_TESTS,
                "user@arg.test",
                Instant.now().plusSeconds(120)
        );
        when(authenticationService.introspect(argThat(req -> token.equals(req.getToken()))))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        Jwt decoded = customerJwtDecoderUnderTest.decode(token);
        assertEquals("user@arg.test", decoded.getSubject());
    }

    private static String buildNimbusHs512CompactJwt(String signerKey, String subject, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
        signedJWT.sign(new MACSigner(signerKey.getBytes()));

        return signedJWT.serialize();
    }
}
