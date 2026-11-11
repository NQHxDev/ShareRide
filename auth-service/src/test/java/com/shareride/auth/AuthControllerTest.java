package com.shareride.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.controller.AccountController;
import com.shareride.auth.controller.AuthController;
import com.shareride.auth.controller.SessionController;
import com.shareride.auth.controller.WellKnownController;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RefreshTokenRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.common.CommonAutoConfiguration;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {
            AuthController.class,
            AccountController.class,
            SessionController.class,
            WellKnownController.class
        })
@AutoConfigureMockMvc(addFilters = false)
@Import(CommonAutoConfiguration.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean private AuthService authService;
    @MockitoBean private SessionManagementService sessionManagementService;
    @MockitoBean private AccountManagementService accountManagementService;

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return HTTP 201 Created with BaseResponse")
    void testRegisterSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountResponse accountResponse =
                AccountResponse.builder()
                        .accountId(accountId)
                        .email("test.rider@shareride.com")
                        .phoneNumber("+84900123456")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .permissions(Set.of("ride:create", "ride:read"))
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(accountResponse);

        RegisterRequest request =
                RegisterRequest.builder()
                        .email("test.rider@shareride.com")
                        .password("SecurePass123")
                        .phoneNumber("+84900123456")
                        .roleCode("ROLE_RIDER")
                        .build();

        mockMvc.perform(
                        post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_REGISTERED))
                .andExpect(jsonPath("$.data.email").value("test.rider@shareride.com"))
                .andExpect(jsonPath("$.data.accountId").value(accountId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return HTTP 200 with tokens and session")
    void testLoginSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthResponse authResponse =
                AuthResponse.builder()
                        .accessToken("access-token-jwt")
                        .refreshToken("refresh-token-uuid")
                        .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                        .sessionId(sessionId)
                        .account(
                                AccountResponse.builder()
                                        .accountId(accountId)
                                        .email("rider@shareride.com")
                                        .build())
                        .build();

        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(authResponse);

        LoginRequest request =
                LoginRequest.builder().email("rider@shareride.com").password("Secret123").build();

        mockMvc.perform(
                        post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.LOGIN_SUCCESS))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-jwt"))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh should return HTTP 200 with new tokens")
    void testRefreshTokenSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthResponse authResponse =
                AuthResponse.builder()
                        .accessToken("new-access-token")
                        .refreshToken("new-refresh-token")
                        .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                        .sessionId(sessionId)
                        .account(
                                AccountResponse.builder()
                                        .accountId(accountId)
                                        .email("rider@shareride.com")
                                        .build())
                        .build();

        when(authService.refreshToken(eq("current-refresh-token"), any(), any()))
                .thenReturn(authResponse);

        RefreshTokenRequest request =
                RefreshTokenRequest.builder().refreshToken("current-refresh-token").build();

        mockMvc.perform(
                        post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.REFRESH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.TOKEN_REFRESHED))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should revoke tokens and return HTTP 200")
    void testLogoutSuccess() throws Exception {
        doNothing().when(authService).logout(any(), any());

        mockMvc.perform(
                        post(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.LOGOUT)
                                .header(
                                        AuthConstants.HEADER_AUTHORIZATION,
                                        AuthConstants.BEARER_PREFIX + "token-to-revoke")
                                .param(AuthConstants.PARAM_REFRESH_TOKEN, "rt-to-revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/auth/accounts/{id} unauthenticated should return HTTP 401")
    void testGetAccountUnauthenticated() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(get(AuthConstants.Endpoint.BASE_PATH + "/accounts/" + accountId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/auth/accounts/{id} viewing another account without ADMIN role should"
                    + " return HTTP 403")
    void testGetAccountForbiddenForOtherAccount() throws Exception {
        UUID targetAccountId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        UserContext regularUser =
                UserContext.builder()
                        .userId(currentUserId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build();
        UserContextHolder.setContext(regularUser);

        mockMvc.perform(get(AuthConstants.Endpoint.BASE_PATH + "/accounts/" + targetAccountId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/accounts/{id} viewing own account should return HTTP 200")
    void testGetAccountOwnAccountSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountResponse accountResponse =
                AccountResponse.builder()
                        .accountId(accountId)
                        .email("found@shareride.com")
                        .phoneNumber("+84999888777")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        UserContext regularUser =
                UserContext.builder()
                        .userId(accountId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build();
        UserContextHolder.setContext(regularUser);

        when(accountManagementService.getAccountById(accountId)).thenReturn(accountResponse);

        mockMvc.perform(get(AuthConstants.Endpoint.BASE_PATH + "/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.data.email").value("found@shareride.com"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/auth/accounts/{id} viewing any account with ADMIN role should return HTTP"
                    + " 200")
    void testGetAccountAdminRoleSuccess() throws Exception {
        UUID targetAccountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AccountResponse accountResponse =
                AccountResponse.builder()
                        .accountId(targetAccountId)
                        .email("target@shareride.com")
                        .phoneNumber("+84999888777")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        UserContext adminUser =
                UserContext.builder()
                        .userId(adminId.toString())
                        .roles(Set.of("ROLE_ADMIN"))
                        .build();
        UserContextHolder.setContext(adminUser);

        when(accountManagementService.getAccountById(targetAccountId)).thenReturn(accountResponse);

        mockMvc.perform(get(AuthConstants.Endpoint.BASE_PATH + "/accounts/" + targetAccountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value(targetAccountId.toString()))
                .andExpect(jsonPath("$.data.email").value("target@shareride.com"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/auth/sessions viewing other account sessions without ADMIN should return"
                    + " HTTP 403")
    void testGetActiveSessionsForbiddenForOtherAccount() throws Exception {
        UUID targetAccountId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        UserContext regularUser =
                UserContext.builder()
                        .userId(currentUserId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build();
        UserContextHolder.setContext(regularUser);

        mockMvc.perform(
                        get(AuthConstants.Endpoint.BASE_PATH + "/sessions")
                                .param("accountId", targetAccountId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/auth/sessions/{sessionId} should revoke session and return HTTP 200")
    void testRevokeSessionSuccess() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        doNothing().when(sessionManagementService).revokeSession(sessionId, accountId);

        mockMvc.perform(
                        delete(AuthConstants.Endpoint.BASE_PATH + "/sessions/" + sessionId)
                                .param("accountId", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.SESSION_REVOKED));
    }

    @Test
    @DisplayName("GET /api/v1/auth/jwks.json should return RFC 7517 JWK Set")
    void testGetJwksFromAuthEndpoint() throws Exception {
        Map<String, Object> jwks =
                Map.of(
                        "keys",
                        List.of(
                                Map.of(
                                        "kty", "OKP",
                                        "crv", "Ed25519",
                                        "kid", "shareride-auth-key-1",
                                        "x", "sample-public-key-bytes")));

        when(authService.getJwks()).thenReturn(jwks);

        mockMvc.perform(get(AuthConstants.Endpoint.BASE_PATH + AuthConstants.Endpoint.JWKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("OKP"))
                .andExpect(jsonPath("$.keys[0].crv").value("Ed25519"))
                .andExpect(jsonPath("$.keys[0].kid").value("shareride-auth-key-1"));
    }

    @Test
    @DisplayName("GET /.well-known/jwks.json should return RFC 7517 JWK Set")
    void testGetJwksFromWellKnownEndpoint() throws Exception {
        Map<String, Object> jwks =
                Map.of(
                        "keys",
                        List.of(
                                Map.of(
                                        "kty", "OKP",
                                        "crv", "Ed25519",
                                        "kid", "shareride-auth-key-1",
                                        "x", "sample-public-key-bytes")));

        when(authService.getJwks()).thenReturn(jwks);

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("OKP"))
                .andExpect(jsonPath("$.keys[0].crv").value("Ed25519"))
                .andExpect(jsonPath("$.keys[0].kid").value("shareride-auth-key-1"));
    }
}
