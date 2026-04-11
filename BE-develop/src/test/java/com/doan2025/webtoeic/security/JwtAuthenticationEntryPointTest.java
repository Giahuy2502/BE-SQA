package com.doan2025.webtoeic.security;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link JwtAuthenticationEntryPoint}.
 * <p>
 * <b>Database (CheckDB / Rollback):</b> No database access. Response is written to an in-memory
 * {@link MockHttpServletResponse} only.
 */
class JwtAuthenticationEntryPointTest {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint = new JwtAuthenticationEntryPoint();
    private final ObjectMapper jsonObjectMapper = new ObjectMapper();

    /**
     * Test Case ID: UTC-SEC-JEP-001
     * Objective: {@link JwtAuthenticationEntryPoint#commence} sets HTTP 401 and JSON body matching {@link ResponseCode#UNAUTHENTICATED}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void commence_sets401AndJsonBody_matchingUnauthenticated() throws Exception {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        AuthenticationException authenticationException = new BadCredentialsException("ignored-by-entry-point");

        jwtAuthenticationEntryPoint.commence(httpServletRequest, httpServletResponse, authenticationException);

        assertThat(httpServletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(httpServletResponse.getContentType()).contains("application/json");

        JsonNode responseJson = jsonObjectMapper.readTree(httpServletResponse.getContentAsString());
        assertThat(responseJson.get("code").asInt()).isEqualTo(ResponseCode.UNAUTHENTICATED.getCode());
        assertThat(responseJson.get("message").asText()).isEqualTo(ResponseCode.UNAUTHENTICATED.getMessage());
    }
}
