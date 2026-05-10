package com.doan2025.webtoeic.config;

import com.doan2025.webtoeic.security.CustomerJwtDecoder;
import com.doan2025.webtoeic.security.AuthenticationService;
import com.doan2025.webtoeic.dto.request.IntrospectRequest;
import com.doan2025.webtoeic.dto.response.IntrospectResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.CorsFilter;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kiểm thử {@link SecurityConfig} theo checklist:
 * - filterChain(HttpSecurity): permitAll/public endpoints, authenticated endpoints, JWT behaviors, CSRF disabled
 * - corsFilter(): CORS origins/patterns/methods/headers/credentials/maxAge/path mapping
 * - jwtAuthenticationConverter(): authority prefix behavior
 * - passwordEncoder(): BCrypt strength 10
 */
@SpringBootTest(classes = SecurityConfigTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // HS512 yêu cầu key đủ dài (>= 64 bytes)
        "jwt.signerKey=0123456789012345678901234567890123456789012345678901234567890123"
})
class SecurityConfigTest {

    private static final String SIGNER_KEY =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthenticationService authenticationService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            SecurityConfig.class,
            CustomerJwtDecoder.class,
            PublicEndpointsController.class,
            PrivateEndpointsController.class
    })
    static class TestApp {
    }

    @BeforeEach
    void filterChain_DefaultState_IntrospectValid() throws Exception {
        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());
    }

    // --------------------------
    // 1) filterChain(HttpSecurity)
    // --------------------------

    static Stream<String> publicPostEndpoints() {
        return Stream.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/verify-email",
                "/api/v1/auth/verify-otp",
                "/api/v1/auth/reset-password",
                "/api/v1/cloud/upload",
                "/api/v1/cloud/delete",
                "/api/v1/post/get-posts",
                "/api/v1/course/get-courses",
                "/user"
        );
    }

    /**
     * TC-SEC-001 to TC-SEC-010: Public POST endpoints (parameterized) 
     * Muc dich: Xac minh cac endpoint public POST duoc cho phep tuy khong co token
     * Endpoints: /api/v1/auth/login, /api/v1/auth/register, /api/v1/auth/verify-email,
     *            /api/v1/auth/verify-otp, /api/v1/auth/reset-password, /api/v1/cloud/upload,
     *            /api/v1/cloud/delete, /api/v1/post/get-posts, /api/v1/course/get-courses, /user
     * Du lieu dau vao: POST request khong co Authorization header
     * Ket qua ky vong: HTTP 200 OK
     */
    @ParameterizedTest
    @MethodSource("publicPostEndpoints")
    void filterChain_PublicEndpointsPost_ShouldPermitWithoutAuthentication(String path) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    static Stream<String> publicGetEndpoints() {
        return Stream.of(
                "/api/v1/post",
                "/api/v1/course",
                "/api/v1/payment/return",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config",
                "/api/v1/category/role",
                "/api/v1/category/gender",
                "/api/v1/category/course",
                "/api/v1/category/status-order",
                "/api/v1/category/status-schedule",
                "/api/v1/category/status-class",
                "/api/v1/category/status-attendance",
                "/api/v1/category/type-class-notification",
                "/api/v1/category/join-class-status"
        );
    }

    /**
     * TC-SEC-011 to TC-SEC-024: Public GET endpoints (parameterized)
     * Muc dich: Xac minh cac endpoint public GET duoc cho phep tuy khong co token
     * Endpoints: /api/v1/post, /api/v1/course, /api/v1/payment/return, /v3/api-docs,
     *            /v3/api-docs/swagger-config, /api/v1/category/*, etc (14 endpoints)
     * Du lieu dau vao: GET request khong co Authorization header
     * Ket qua ky vong: HTTP 200 OK
     */
    @ParameterizedTest
    @MethodSource("publicGetEndpoints")
    void filterChain_PublicEndpointsGet_ShouldPermitWithoutAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    /**
     * TC-SEC-025: Swagger UI redirect tuy khong co token
     * Muc dich: Xac minh /swagger-ui.html la public endpoint va redirect
     * Du lieu dau vao: GET /swagger-ui.html khong co Authorization
     * Ket qua ky vong: HTTP 3xx redirect (public, khong can xac thuc)
     */
    @Test
    void filterChain_SwaggerUiHtml_PublicEndpoint_ShouldRedirectWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * TC-SEC-026: CORS preflight OPTIONS request
     * Muc dich: Xac minh OPTIONS request cho CORS preflight duoc cho phep
     * Du lieu dau vao: OPTIONS /any/path tuy khong co token
     * Ket qua ky vong: HTTP 200 OK (CORS preflight)
     */
    @Test
    void filterChain_OptionsRequestAnyPath_ShouldPermitWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/any/path/for/preflight"))
                .andExpect(status().isOk());
    }

    /**
     * TC-SEC-027: Private endpoint khong co token
     * Muc dich: Xac minh endpoint private can token
     * Du lieu dau vao: GET /api/v1/some-private-endpoint khong co Authorization
     * Ket qua ky vong: HTTP 401 Unauthorized
     */
    @Test
    void filterChain_AuthenticatedEndpointWithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/some-private-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TC-SEC-028: Private endpoint voi token hop le
     * Muc dich: Xac minh token hop le duoc chap nhan
     * Du lieu dau vao: GET /api/v1/some-private-endpoint + header Authorization: Bearer {valid_token}
     * Ket qua ky vong: HTTP 200 OK
     */
    @Test
    void filterChain_AuthenticatedEndpointWithValidJwt_ShouldReturn200() throws Exception {
        String token = buildJwt("user@example.com", "ROLE_MANAGER", Instant.now().plusSeconds(3600), SIGNER_KEY);
        mockMvc.perform(get("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * TC-SEC-029: Private endpoint voi token het han
     * Muc dich: Xac minh token het han bi tu choi
     * Du lieu dau vao: GET /api/v1/some-private-endpoint + Bearer token het han
     * Ket qua ky vong: HTTP 401 Unauthorized
     */
    @Test
    void filterChain_AuthenticatedEndpointWithExpiredJwt_ShouldReturn401() throws Exception {
        String token = buildJwt("user@example.com", "ROLE_MANAGER", Instant.now().minusSeconds(5), SIGNER_KEY);
        mockMvc.perform(get("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TC-SEC-030: Private endpoint voi token co signature khong dung
     * Muc dich: Xac minh token ky voi key khac bi tu choi
     * Du lieu dau vao: GET /api/v1/some-private-endpoint + Bearer token (signer key khac)
     * Ket qua ky vong: HTTP 401 Unauthorized
     */
    @Test
    void filterChain_AuthenticatedEndpointWithInvalidSignatureJwt_ShouldReturn401() throws Exception {
        String token = buildJwt("user@example.com", "ROLE_MANAGER", Instant.now().plusSeconds(3600),
                "different-signer-key-different-signer-key-different-signer-key-123456");
        mockMvc.perform(get("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TC-SEC-031: POST private endpoint khong co token
     * Muc dich: Xac minh POST endpoint private can token
     * Du lieu dau vao: POST /api/v1/some-private-endpoint khong co Authorization
     * Ket qua ky vong: HTTP 401 Unauthorized
     */
    @Test
    void filterChain_PostNonPublicEndpointWithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/some-private-endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TC-SEC-032: CSRF disabled - POST voi token hop le khong can CSRF token
     * Muc dich: Xac minh CSRF protection bi disable cho JWT auth
     * Du lieu dau vao: POST + Bearer token, khong co CSRF token
     * Ket qua ky vong: HTTP 200 OK (CSRF bypass vi JWT auth)
     */
    @Test
    void filterChain_CsrfDisabled_PostWithoutCsrfTokenWithValidJwt_ShouldPass() throws Exception {
        String token = buildJwt("user@example.com", "ROLE_MANAGER", Instant.now().plusSeconds(3600), SIGNER_KEY);
        mockMvc.perform(post("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    /**
     * TC-SEC-033: POST voi token va CSRF token (khong bat buoc)
     * Muc dich: Xac minh CSRF token van duoc chap nhan (khong bat buoc)
     * Du lieu dau vao: POST + Bearer token + CSRF token
     * Ket qua ky vong: HTTP 200 OK
     */
    @Test
    void filterChain_CsrfDisabled_PostWithCsrfTokenWithValidJwt_ShouldAlsoPass() throws Exception {
        String token = buildJwt("user@example.com", "ROLE_MANAGER", Instant.now().plusSeconds(3600), SIGNER_KEY);
        mockMvc.perform(post("/api/v1/some-private-endpoint")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // -------------
    // 2) corsFilter()
    // -------------

    /**
     * TC-SEC-034: CORS allowed origin exact match
     * Muc dich: Xac minh origin localhost:5173 duoc phep
     * Du lieu dau vao: OPTIONS + Origin: http://localhost:5173
     * Ket qua ky vong: HTTP 200 + CORS headers voi Allow-Origin = localhost:5173
     */
    @Test
    void corsFilter_AllowedOriginExact_ShouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    /**
     * TC-SEC-035: CORS disallowed origin
     * Muc dich: Xac minh origin evil.com bi chan
     * Du lieu dau vao: OPTIONS + Origin: http://evil.com
     * Ket qua ky vong: HTTP 403 Forbidden, khong co CORS header
     */
    @Test
    void corsFilter_DisallowedOrigin_ShouldNotReturnAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://evil.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /**
     * TC-SEC-036: CORS allowed origin pattern 192.168.x.x
     * Muc dich: Xac minh IP pattern 192.168.* duoc phep
     * Du lieu dau vao: OPTIONS + Origin: http://192.168.1.100
     * Ket qua ky vong: HTTP 200 + Allow-Origin = 192.168.1.100
     */
    @Test
    void corsFilter_AllowedOriginPattern192_ShouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.100")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.100"));
    }

    /**
     * TC-SEC-037: CORS allowed origin pattern 10.x.x.x
     * Muc dich: Xac minh IP pattern 10.*.*.* duoc phep
     * Du lieu dau vao: OPTIONS + Origin: http://10.10.5.5
     * Ket qua ky vong: HTTP 200 + Allow-Origin = 10.10.5.5
     */
    @Test
    void corsFilter_AllowedOriginPattern1010_ShouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://10.10.5.5")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://10.10.5.5"));
    }

    /**
     * TC-SEC-038: CORS allowed origin pattern 172.17.x.x
     * Muc dich: Xac minh IP pattern 172.17.*.* duoc phep
     * Du lieu dau vao: OPTIONS + Origin: http://172.17.0.1
     * Ket qua ky vong: HTTP 200 + Allow-Origin = 172.17.0.1
     */
    @Test
    void corsFilter_AllowedOriginPattern17217_ShouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://172.17.0.1")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://172.17.0.1"));
    }

    /**
     * TC-SEC-039: CORS allowed origin pattern 116.111.x.x
     * Muc dich: Xac minh IP pattern 116.111.*.* duoc phep
     * Du lieu dau vao: OPTIONS + Origin: http://116.111.50.50
     * Ket qua ky vong: HTTP 200 + Allow-Origin = 116.111.50.50
     */
    @Test
    void corsFilter_AllowedOriginPattern116111_ShouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://116.111.50.50")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://116.111.50.50"));
    }

    /**
     * TC-SEC-040: CORS disallowed IP pattern 172.18.x.x
     * Muc dich: Xac minh IP pattern 172.18.* bi chan (chi 172.17.* duoc phep)
     * Du lieu dau vao: OPTIONS + Origin: http://172.18.0.1
     * Ket qua ky vong: HTTP 403 Forbidden, khong co CORS header
     */
    @Test
    void corsFilter_DisallowedOriginPattern_ShouldNotReturnAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://172.18.0.1")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /**
     * TC-SEC-041: CORS allowed HTTP methods
     * Muc dich: Xac minh cac method GET, POST, PUT, DELETE, OPTIONS duoc phep
     * Du lieu dau vao: OPTIONS voi Access-Control-Request-Method: PUT
     * Ket qua ky vong: HTTP 200 + Allow-Methods = GET, POST, PUT, DELETE, OPTIONS
     */
    @Test
    void corsFilter_AllowedMethods_ShouldContainConfiguredMethods() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("DELETE")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("OPTIONS")));
    }

    /**
     * TC-SEC-042: CORS disallowed method PATCH
     * Muc dich: Xac minh method PATCH bi chan (chi GET, POST, PUT, DELETE duoc phep)
     * Du lieu dau vao: OPTIONS voi Access-Control-Request-Method: PATCH
     * Ket qua ky vong: HTTP 403 Forbidden, khong co CORS header
     */
    @Test
    void corsFilter_DisallowedMethodPatch_ShouldNotReturnAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /**
     * TC-SEC-043: CORS allowed custom headers
     * Muc dich: Xac minh custom headers duoc phep (wildcard *)
     * Du lieu dau vao: OPTIONS voi Access-Control-Request-Headers: X-Custom-Header
     * Ket qua ky vong: HTTP 200 + Allow-Headers chwa X-Custom-Header
     */
    @Test
    void corsFilter_AllowedHeadersWildcard_ShouldReturnAllowHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/some-private-endpoint")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom-Header"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Custom-Header")));
    }

    // ---------------------------
    // 3) jwtAuthenticationConverter()
    // ---------------------------

    /**
     * TC-SEC-044: JWT converter - role authority khong them prefix
     * Muc dich: Xac minh scope claim voi ROLE_ADMIN khong them prefix
     * Du lieu dau vao: JWT voi scope = "ROLE_ADMIN"
     * Ket qua ky vong: Authorities = ["ROLE_ADMIN"] (khong them SCOPE_)
     */
    @Test
    void jwtAuthenticationConverter_ScopeClaimWithRoleAuthority_ShouldNotAddScopePrefix() {
        SecurityConfig config = new SecurityConfig(null);
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("scope", "ROLE_ADMIN")
                .subject("user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var auth = converter.convert(jwt);
        assertNotNull(auth);
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertEquals(List.of("ROLE_ADMIN"), authorities);
    }

    /**
     * TC-SEC-045: JWT converter - multiple roles
     * Muc dich: Xac minh scope claim voi nhieu role
     * Du lieu dau vao: JWT voi scope = "ROLE_USER ROLE_TEACHER"
     * Ket qua ky vong: Authorities = ["ROLE_USER", "ROLE_TEACHER"]
     */
    @Test
    void jwtAuthenticationConverter_ScopeClaimMultipleRoles_ShouldReturnExactAuthorities() {
        SecurityConfig config = new SecurityConfig(null);
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("scope", "ROLE_USER ROLE_TEACHER")
                .subject("user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var auth = converter.convert(jwt);
        assertNotNull(auth);
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertEquals(List.of("ROLE_USER", "ROLE_TEACHER"), authorities);
    }

    /**
     * TC-SEC-046: JWT converter - khong co scope claim
     * Muc dich: Xac minh JWT khong co scope tra ve authorities rong
     * Du lieu dau vao: JWT khong co scope claim
     * Ket qua ky vong: Authorities = [] (rong)
     */
    @Test
    void jwtAuthenticationConverter_NoScopeClaim_ShouldReturnEmptyAuthorities() {
        SecurityConfig config = new SecurityConfig(null);
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var auth = converter.convert(jwt);
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty());
    }

    // ----------------
    // 4) passwordEncoder()
    // ----------------

    /**
     * TC-SEC-047: Password encoder BCrypt strength 10
     * Muc dich: Xac minh password encoder dung BCrypt strength 10
     * Du lieu dau vao: Password = "password"
     * Ket qua ky vong: Encoded hash co $10$ (strength 10), matches chay dung
     */
    @Test
    void passwordEncoder_DefaultState_ShouldCreateBCryptStrength10() {
        SecurityConfig config = new SecurityConfig(null);
        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);

        String encoded1 = encoder.encode("password");
        String encoded2 = encoder.encode("password");

        assertNotNull(encoded1);
        assertNotEquals("password", encoded1);
        assertTrue(encoder.matches("password", encoded1));
        assertFalse(encoder.matches("wrongpassword", encoded1));
        assertNotEquals(encoded1, encoded2);
        assertTrue(encoded1.contains("$10$"), "BCrypt hash should include strength $10$");
    }

    /**
     * TC-SEC-048: CORS filter initialization
     * Muc dich: Xac minh CorsFilter bean duoc tao thanh cong
     * Du lieu dau vao: SecurityConfig.corsFilter() duoc goi
     * Ket qua ky vong: CorsFilter instance khong null
     */
    @Test
    void corsFilter_DefaultState_ShouldNotBeNull() {
        SecurityConfig config = new SecurityConfig(null);
        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);
    }

    // ----------------
    // Test-only controllers
    // ----------------

    @RestController
    static class PublicEndpointsController {

        @PostMapping({
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/verify-email",
                "/api/v1/auth/verify-otp",
                "/api/v1/auth/reset-password",
                "/api/v1/cloud/upload",
                "/api/v1/cloud/delete",
                "/api/v1/post/get-posts",
                "/api/v1/course/get-courses",
                "/user"
        })
        public String okPost() {
            return "ok";
        }

        @GetMapping({
                "/api/v1/post",
                "/api/v1/course",
                "/api/v1/payment/return"
        })
        public String okGet() {
            return "ok";
        }

        @GetMapping("/api/v1/category/{any}")
        public String okCategory(@PathVariable("any") String any) {
            return any;
        }

        @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
        public void okOptions() {
        }
    }

    @RestController
    static class PrivateEndpointsController {
        @GetMapping("/api/v1/some-private-endpoint")
        public String privateGet() {
            return "private-ok";
        }

        @PostMapping("/api/v1/some-private-endpoint")
        public String privatePost() {
            return "private-ok";
        }
    }

    private static String buildJwt(String subject, String scope, Instant expiresAt, String signerKey) {
        var key = Keys.hmacShaKeyFor(signerKey.getBytes());
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .claim("scope", scope)
                .signWith(key)
                .compact();
    }
}
