package com.doan2025.webtoeic.controller;

import com.doan2025.webtoeic.constants.enums.ResponseCode;
import com.doan2025.webtoeic.dto.request.AuthenticationRequest;
import com.doan2025.webtoeic.dto.request.RegisterRequest;
import com.doan2025.webtoeic.dto.request.UserRequest;
import com.doan2025.webtoeic.dto.request.VerifyRequest;
import com.doan2025.webtoeic.dto.response.AuthenticationResponse;
import com.doan2025.webtoeic.dto.response.VerifyResponse;
import com.doan2025.webtoeic.security.AuthenticationService;
import com.doan2025.webtoeic.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AuthController} using {@link WebMvcTest}.
 * <p>
 * {@link AuthenticationService} and {@link UserService} are {@link MockBean}s — no real database.
 * <p>
 * <b>CheckDB:</b> For this layer, "verification" is {@code verify(mock).method(...)} proving the controller
 * invoked the correct service operation with no persistence side effects.
 * <p>
 * <b>Rollback:</b> N/A (no datasource in this test slice).
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private UserService userService;

    /**
     * Test Case ID: UTC-SEC-AC-001
     * Objective: POST {@code /api/v1/auth/login} delegates to {@link AuthenticationService#authenticate} and returns success envelope.
     * CheckDB: N/A — verify service interaction only. Rollback: N/A.
     */
    @Test
    void login_delegatesToService_andReturnsWrappedBody() throws Exception {
        when(authenticationService.authenticate(any(AuthenticationRequest.class)))
                .thenReturn(AuthenticationResponse.builder()
                        .token("jwt-token")
                        .authenticated(true)
                        .role(4)
                        .build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthenticationRequest.builder()
                                        .email("u@test.com")
                                        .password("secret12")
                                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.role").value(4));

        verify(authenticationService).authenticate(any(AuthenticationRequest.class));
    }

    /**
     * Test Case ID: UTC-SEC-AC-002
     * Objective: POST {@code /api/v1/auth/register} calls {@link AuthenticationService#register}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void register_callsService_andReturnsSuccessEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RegisterRequest.builder()
                                        .email("new@test.com")
                                        .password("password12")
                                        .firstName("A")
                                        .lastName("B")
                                        .role(4)
                                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(authenticationService).register(any(RegisterRequest.class));
    }

    /**
     * Test Case ID: UTC-SEC-AC-003
     * Objective: POST {@code /api/v1/auth/verify-email} calls {@link AuthenticationService#verifyMail}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void verifyEmail_callsService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                VerifyRequest.builder().email("e@test.com").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(authenticationService).verifyMail(any(VerifyRequest.class));
    }

    /**
     * Test Case ID: UTC-SEC-AC-004
     * Objective: POST {@code /api/v1/auth/verify-otp} returns token payload from service.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void verifyOtp_returnsTokenPayload() throws Exception {
        when(authenticationService.verify_otp(any(VerifyRequest.class)))
                .thenReturn(VerifyResponse.builder().token("reset-jwt").build());

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                VerifyRequest.builder()
                                        .email("e@test.com")
                                        .otp(123456)
                                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("reset-jwt"));

        verify(authenticationService).verify_otp(any(VerifyRequest.class));
    }

    /**
     * Test Case ID: UTC-SEC-AC-005
     * Objective: GET {@code /api/v1/auth/logout} calls {@link AuthenticationService#logout}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void logout_callsService() throws Exception {
        mockMvc.perform(get("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(authenticationService).logout(any());
    }

    /**
     * Test Case ID: UTC-SEC-AC-006
     * Objective: GET {@code /api/v1/auth/refresh} calls {@link AuthenticationService#refreshToken}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void refresh_callsService() throws Exception {
        when(authenticationService.refreshToken(any()))
                .thenReturn(AuthenticationResponse.builder()
                        .token("refreshed")
                        .authenticated(true)
                        .role(4)
                        .build());

        mockMvc.perform(get("/api/v1/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("refreshed"));

        verify(authenticationService).refreshToken(any());
    }

    /**
     * Test Case ID: UTC-SEC-AC-007
     * Objective: POST {@code /api/v1/auth/reset-password} delegates to {@link UserService#resetPassword}.
     * CheckDB: N/A. Rollback: N/A.
     */
    @Test
    void resetPassword_delegatesToUserService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UserRequest.builder()
                                        .email("u@test.com")
                                        .token("tok")
                                        .password("newpass12")
                                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()));

        verify(userService).resetPassword(any(UserRequest.class));
    }
}
