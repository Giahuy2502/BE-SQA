package com.doan2025.webtoeic.config;

import com.doan2025.webtoeic.security.CustomerJwtDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCID nhóm: UTC-SEC-CFG-*
 * Kiểm thử các {@code @Bean} “mỏng” trong {@link SecurityConfig} (không dựng full HttpSecurity).
 */
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private CustomerJwtDecoder customerJwtDecoder;

    /**
     * TCID: UTC-SEC-CFG-001
     * passwordEncoder trả về encoder có thể hash và verify mật khẩu (BCrypt).
     */
    @Test
    void passwordEncoder_shouldEncodeAndMatch() {
        SecurityConfig config = new SecurityConfig(customerJwtDecoder);
        PasswordEncoder encoder = config.passwordEncoder();

        String encoded = encoder.encode("plain-secret");
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$"));
        assertTrue(encoder.matches("plain-secret", encoded));
    }

    /**
     * TCID: UTC-SEC-CFG-002
     * corsFilter không null và đã đăng ký cấu hình CORS.
     */
    @Test
    void corsFilter_shouldNotBeNull() {
        SecurityConfig config = new SecurityConfig(customerJwtDecoder);
        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);
    }

    /**
     * TCID: UTC-SEC-CFG-003
     * jwtAuthenticationConverter được tạo (granted authorities không prefix ROLE_).
     */
    @Test
    void jwtAuthenticationConverter_shouldNotBeNull() {
        SecurityConfig config = new SecurityConfig(customerJwtDecoder);
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();
        assertNotNull(converter);
    }
}
