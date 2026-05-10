package com.doan2025.webtoeic.security;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Unit tests for {@link JwtAuthenticationEntryPoint}.
 */
class JwtAuthenticationEntryPointTest {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint = new JwtAuthenticationEntryPoint();
    private final ObjectMapper jsonObjectMapper = new ObjectMapper();

    /**
     * TC-ENTRY-001: Khi xác thực thất bại, HTTP status code trả về UNAUTHENTICATED
     * Mục đích: Xác minh entry point trả về status code chính xác từ ResponseCode
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: httpServletResponse.getStatus() = ResponseCode.UNAUTHENTICATED.getStatusCode().value()
     */
    @Test
    void commence_DefaultState_ShouldSetStatusUnauthenticated() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        AuthenticationException authenticationException = new BadCredentialsException("ignored-by-entry-point");

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, authenticationException);

        assertThat(httpServletResponse.getStatus())
                .isEqualTo(ResponseCode.UNAUTHENTICATED.getStatusCode().value());
    }

    /**
     * TC-ENTRY-002: Khi xác thực thất bại, response content-type là application/json
     * Mục đích: Xác minh response được đặt content-type chính xác
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: httpServletResponse.getContentType() chứa "application/json"
     */
    @Test
    void commence_DefaultState_ShouldSetContentTypeApplicationJson() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, new BadCredentialsException("x"));

        assertThat(httpServletResponse.getContentType()).contains("application/json");
    }

    /**
     * TC-ENTRY-003: Khi xác thực thất bại, JSON response có field 'code' chính xác
     * Mục đích: Xác minh response JSON chứa code field đúng giá trị
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: responseJson.get("code").asInt() = ResponseCode.UNAUTHENTICATED.getCode()
     */
    @Test
    void commence_DefaultState_ShouldWriteJsonBodyWithExpectedCode() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, new BadCredentialsException("x"));

        JsonNode responseJson = jsonObjectMapper.readTree(httpServletResponse.getContentAsString());
        assertThat(responseJson.get("code").asInt()).isEqualTo(ResponseCode.UNAUTHENTICATED.getCode());
    }

    /**
     * TC-ENTRY-004: Khi xác thực thất bại, JSON response có field 'message' chính xác
     * Mục đích: Xác minh response JSON chứa message field đúng giá trị
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: responseJson.get("message").asText() = ResponseCode.UNAUTHENTICATED.getMessage()
     */
    @Test
    void commence_DefaultState_ShouldWriteJsonBodyWithExpectedMessage() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, new BadCredentialsException("x"));

        JsonNode responseJson = jsonObjectMapper.readTree(httpServletResponse.getContentAsString());
        assertThat(responseJson.get("message").asText()).isEqualTo(ResponseCode.UNAUTHENTICATED.getMessage());
    }

    /**
     * TC-ENTRY-005: Khi xác thực thất bại, response body là JSON hợp lệ
     * Mục đích: Xác minh response body là JSON đúng định dạng, ObjectMapper có thể parse
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: jsonObjectMapper.readTree(responseBody) không null, không ném exception
     */
    @Test
    void commence_DefaultState_ShouldWriteValidJsonBody() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, new BadCredentialsException("x"));

        assertThat(jsonObjectMapper.readTree(httpServletResponse.getContentAsString())).isNotNull();
    }

    /**
     * TC-ENTRY-006: Khi xác thực thất bại, JSON response field 'data' phải là null
     * Mục đích: Xác minh response không rò rỉ dữ liệu nhạy cảm qua field 'data'
     *           Theo thiết kế API, khi lỗi xác thực, field data phải null hoặc không tồn tại.
     *           Assertion bắt buộc (không dùng if lỏng lẻo).
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: data field null hoặc absent – assert bắt buộc cả hai trường hợp
     */
    @Test
    void commence_DefaultState_ShouldHaveNullOrAbsentDataField() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, new BadCredentialsException("x"));

        JsonNode responseJson = jsonObjectMapper.readTree(httpServletResponse.getContentAsString());
        // Assertion bắt buộc: data phải không tồn tại HOẶC nếu tồn tại thì phải là null
        boolean dataIsNullOrAbsent = !responseJson.has("data")
                || responseJson.get("data").isNull();
        assertThat(dataIsNullOrAbsent)
                .as("Field 'data' phải là null hoặc absent trong error response – không được có giá trị thực")
                .isTrue();
    }

    /**
     * TC-ENTRY-007: Khi xác thực thất bại, response ghi dữ liệu không rỗng
     * Mục đích: Xác minh response writer.write() được gọi với nội dung
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException, response.getWriter() = PrintWriter spy
     * Kết quả kỳ vọng: verify(printWriter, atLeastOnce()).write(anyString()), stringWriter.toString() isNotBlank()
     */
    @Test
    void commence_DefaultState_ShouldCallWriterWriteWithNonEmptyContent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = new BadCredentialsException("x");

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = spy(new PrintWriter(stringWriter));
        when(response.getWriter()).thenReturn(printWriter);

        jwtAuthenticationEntryPoint.commence(request, response, authException);

        verify(printWriter, atLeastOnce()).write(anyString());
        assertThat(stringWriter.toString()).isNotBlank();
    }

    /**
     * TC-ENTRY-008: Khi xác thực thất bại, response xả buffer sau ghi dữ liệu
     * Mục đích: Xác minh response.flushBuffer() được gọi để đảm bảo client nhận dữ liệu
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException, response = mock
     * Kết quả kỳ vọng: verify(response).flushBuffer() được gọi
     */
    @Test
    void commence_DefaultState_ShouldFlushBufferAfterWriting() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = new BadCredentialsException("x");

        PrintWriter printWriter = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(printWriter);

        jwtAuthenticationEntryPoint.commence(request, response, authException);

        verify(response).flushBuffer();
    }

    /**
     * TC-ENTRY-009: Khi exception là InsufficientAuthenticationException, trả về UNAUTHENTICATED
     * Mục đích: Xác minh handler xử lý InsufficientAuthenticationException đúng cách
     * Dữ liệu đầu vào: AuthenticationException: InsufficientAuthenticationException
     * Kết quả kỳ vọng: httpServletResponse.getStatus() = ResponseCode.UNAUTHENTICATED.getStatusCode().value()
     */
    @Test
    void commence_InsufficientAuthenticationException_ShouldReturnUnauthenticatedResponse() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(
                httpServletRequest,
                httpServletResponse,
                new InsufficientAuthenticationException("insufficient")
        );

        assertThat(httpServletResponse.getStatus())
                .isEqualTo(ResponseCode.UNAUTHENTICATED.getStatusCode().value());
    }

    /**
     * TC-ENTRY-010: Khi exception là BadCredentialsException, trả về UNAUTHENTICATED
     * Mục đích: Xác minh handler xử lý BadCredentialsException đúng cách
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: httpServletResponse.getStatus() = ResponseCode.UNAUTHENTICATED.getStatusCode().value()
     */
    @Test
    void commence_BadCredentialsException_ShouldReturnUnauthenticatedResponse() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(
                httpServletRequest,
                httpServletResponse,
                new BadCredentialsException("bad")
        );

        assertThat(httpServletResponse.getStatus())
                .isEqualTo(ResponseCode.UNAUTHENTICATED.getStatusCode().value());
    }

    /**
     * TC-ENTRY-011: Khi exception là null, handler không ném exception
     * Mục đích: Xác minh handler xử lý null exception một cách graceful
     * Dữ liệu đầu vào: AuthenticationException = null
     * Kết quả kỳ vọng: httpServletResponse.getStatus() = ResponseCode.UNAUTHENTICATED.getStatusCode().value(), không ném exception
     */
    @Test
    void commence_NullAuthenticationException_ShouldNotThrow() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, null);

        assertThat(httpServletResponse.getStatus())
                .isEqualTo(ResponseCode.UNAUTHENTICATED.getStatusCode().value());
    }

    /**
     * TC-ENTRY-012: Khi xác thực thất bại, handler không ném exception nào
     * Mục đích: Xác minh handler xử lý all exception gracefully, không crash
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: assertDoesNotThrow(() -> commence(...)) thành công
     */
    @Test
    void commence_DefaultState_ShouldNotThrowAnyException() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        assertDoesNotThrow(() -> jwtAuthenticationEntryPoint.commence(
                        httpServletRequest,
                        httpServletResponse,
                        new BadCredentialsException("x")
                )
        );
    }

    /**
     * TC-ENTRY-013: Response JSON có đầy đủ cấu trúc ApiResponse
     * Mục đích: Xác minh response JSON có đầy đủ fields: code, message với giá trị chính xác
     * Dữ liệu đầu vào: AuthenticationException: BadCredentialsException
     * Kết quả kỳ vọng: responseJson.has("code") = true, responseJson.has("message") = true, giá trị = ResponseCode.UNAUTHENTICATED
     */
    @Test
    void commence_DefaultState_ShouldWriteApiResponseStructure() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, new BadCredentialsException("x"));

        JsonNode responseJson = jsonObjectMapper.readTree(httpServletResponse.getContentAsString());
        assertThat(responseJson.has("code")).isTrue();
        assertThat(responseJson.has("message")).isTrue();
        assertThat(responseJson.get("code").asInt()).isEqualTo(ResponseCode.UNAUTHENTICATED.getCode());
        assertThat(responseJson.get("message").asText()).isEqualTo(ResponseCode.UNAUTHENTICATED.getMessage());
    }

    /**
     * TC-ENTRY-014: Response JSON không chứa thông tin nội bộ nhạy cảm
     * Mục đích: Đảm bảo response 401 không rò rỉ stack trace hay exception message nội bộ.
     *           Đây là yêu cầu bảo mật – không expose implementation details ra ngoài client.
     * Dữ liệu đầu vào: BadCredentialsException với message nội bộ cụ thể
     * Kết quả kỳ vọng: Response body không chứa message nội bộ, không chứa class name hay "BadCredentials"
     */
    @Test
    void commence_DefaultState_ShouldNotLeakInternalExceptionDetails() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(
                httpServletRequest,
                httpServletResponse,
                new BadCredentialsException("internal-secret-error-detail")
        );

        String responseBody = httpServletResponse.getContentAsString();
        // Response KHÔNG được chứa thông tin nội bộ nhạy cảm
        assertThat(responseBody).doesNotContain("BadCredentials");
        assertThat(responseBody).doesNotContain("stackTrace");
        assertThat(responseBody).doesNotContain("internal-secret-error-detail");
        // Chỉ chứa code và message chuẩn từ ResponseCode
        assertThat(responseBody).contains(String.valueOf(ResponseCode.UNAUTHENTICATED.getCode()));
    }

    /**
     * TC-ENTRY-015: Response nhất quán bất kể nội dung header Authorization trong request
     * Mục đích: Xác minh handler không bị ảnh hưởng bởi Authorization header bất thường,
     *           đảm bảo response 401 luôn nhất quán với cấu trúc chuẩn.
     * Dữ liệu đầu vào: HttpServletRequest có header "Authorization" = "malformed_value", BadCredentialsException
     * Kết quả kỳ vọng: Status = UNAUTHENTICATED, JSON có code và message đúng
     */
    @Test
    void commence_RequestWithMalformedAuthHeader_ShouldReturnConsistentResponse() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getHeader("Authorization")).thenReturn("totally_malformed_not_bearer");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        jwtAuthenticationEntryPoint.commence(
                httpServletRequest,
                httpServletResponse,
                new BadCredentialsException("bad")
        );

        assertThat(httpServletResponse.getStatus())
                .isEqualTo(ResponseCode.UNAUTHENTICATED.getStatusCode().value());
        JsonNode responseJson = jsonObjectMapper.readTree(httpServletResponse.getContentAsString());
        assertThat(responseJson.get("code").asInt()).isEqualTo(ResponseCode.UNAUTHENTICATED.getCode());
        assertThat(responseJson.get("message").asText()).isEqualTo(ResponseCode.UNAUTHENTICATED.getMessage());
    }
}
