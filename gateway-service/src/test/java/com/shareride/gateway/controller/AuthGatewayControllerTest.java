package com.shareride.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Message;
import com.shareride.common.grpc.auth.v1.AccountProto;
import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.GetSessionsRequest;
import com.shareride.common.grpc.auth.v1.GetSessionsResponse;
import com.shareride.common.grpc.auth.v1.JwkKeyProto;
import com.shareride.common.grpc.auth.v1.LoginRequest;
import com.shareride.common.grpc.auth.v1.LoginResponse;
import com.shareride.common.grpc.auth.v1.LogoutRequest;
import com.shareride.common.grpc.auth.v1.LogoutResponse;
import com.shareride.common.grpc.auth.v1.RefreshTokenRequest;
import com.shareride.common.grpc.auth.v1.RefreshTokenResponse;
import com.shareride.common.grpc.auth.v1.RegisterRequest;
import com.shareride.common.grpc.auth.v1.RegisterResponse;
import com.shareride.common.grpc.auth.v1.ResendVerificationRequest;
import com.shareride.common.grpc.auth.v1.ResendVerificationResponse;
import com.shareride.common.grpc.auth.v1.RevokeSessionRequest;
import com.shareride.common.grpc.auth.v1.RevokeSessionResponse;
import com.shareride.common.grpc.auth.v1.SessionProto;
import com.shareride.common.grpc.auth.v1.VerifyEmailRequest;
import com.shareride.common.grpc.auth.v1.VerifyEmailResponse;
import com.shareride.gateway.client.AuthServiceGrpcClient;
import com.shareride.gateway.serializer.ProtobufMessageSerializer;
import com.shareride.gateway.service.GatewayJwksCacheService;
import com.shareride.gateway.service.GatewayRateLimitService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Unit tests for AuthGatewayController - Phase 3 User Story 1 (T018). Tests HTTP-to-gRPC
 * translation logic in isolation using mocked gRPC client and gateway services.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthGatewayController Unit Tests")
class AuthGatewayControllerTest {

    @Mock private AuthServiceGrpcClient authServiceGrpcClient;
    @Mock private GatewayRateLimitService rateLimitService;
    @Mock private GatewayJwksCacheService jwksCacheService;

    @InjectMocks private AuthGatewayController authGatewayController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        SimpleModule protobufModule = new SimpleModule("ProtobufMessageModule");
        protobufModule.addSerializer(Message.class, new ProtobufMessageSerializer());
        JsonMapper jsonMapper = JsonMapper.builder().addModule(protobufModule).build();

        webTestClient =
                WebTestClient.bindToController(authGatewayController)
                        .httpMessageCodecs(
                                configurer -> {
                                    configurer
                                            .defaultCodecs()
                                            .jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                                    configurer
                                            .defaultCodecs()
                                            .jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                                })
                        .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 201 Created")
    void testRegister_shouldReturn201() {
        when(rateLimitService.checkRegisterRateLimit(anyString())).thenReturn(Mono.empty());

        RegisterResponse grpcResponse =
                RegisterResponse.newBuilder()
                        .setAccountId("account-123")
                        .setEmail("test@example.com")
                        .setRoleCode("RIDER")
                        .setVerificationStatus("PENDING")
                        .setCreatedAt("2026-11-18T00:00:00Z")
                        .build();

        when(authServiceGrpcClient.register(any(RegisterRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, Object> registerRequest =
                Map.of(
                        "email", "test@example.com",
                        "password", "password123",
                        "confirmPassword", "password123");

        webTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accountId")
                .isEqualTo("account-123")
                .jsonPath("$.data.email")
                .isEqualTo("test@example.com");

        verify(rateLimitService).checkRegisterRateLimit("test@example.com");
        verify(authServiceGrpcClient).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 OK with tokens")
    void testLogin_shouldReturn200() {
        when(rateLimitService.checkLoginRateLimit(anyString())).thenReturn(Mono.empty());

        LoginResponse grpcResponse =
                LoginResponse.newBuilder()
                        .setAccessToken("access-token-xyz")
                        .setRefreshToken("refresh-token-abc")
                        .setTokenType("Bearer")
                        .setExpiresIn(3600)
                        .setSessionId("session-123")
                        .setAccount(
                                AccountProto.newBuilder()
                                        .setAccountId("account-123")
                                        .setEmail("test@example.com")
                                        .setRoleCode("RIDER")
                                        .build())
                        .build();

        when(authServiceGrpcClient.login(any(LoginRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, Object> loginRequest =
                Map.of(
                        "email", "test@example.com",
                        "password", "password123",
                        "deviceId", "device-123",
                        "deviceName", "iPhone 14");

        webTestClient
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accessToken")
                .isEqualTo("access-token-xyz")
                .jsonPath("$.data.refreshToken")
                .isEqualTo("refresh-token-abc")
                .jsonPath("$.data.sessionId")
                .isEqualTo("session-123");

        verify(rateLimitService).checkLoginRateLimit("test@example.com");
        verify(authServiceGrpcClient).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh should return 200 OK with refreshed tokens")
    void testRefreshToken_shouldReturn200() {
        when(rateLimitService.checkRefreshRateLimit(anyString())).thenReturn(Mono.empty());

        RefreshTokenResponse grpcResponse =
                RefreshTokenResponse.newBuilder()
                        .setAccessToken("new-access-token")
                        .setRefreshToken("new-refresh-token")
                        .setTokenType("Bearer")
                        .setExpiresIn(3600)
                        .build();

        when(authServiceGrpcClient.refreshToken(any(RefreshTokenRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, String> refreshRequest = Map.of("refreshToken", "old-refresh-token");

        webTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accessToken")
                .isEqualTo("new-access-token");

        verify(rateLimitService).checkRefreshRateLimit("old-refresh-token");
        verify(authServiceGrpcClient).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should return 200 OK")
    void testLogout_shouldReturn200() {
        LogoutResponse grpcResponse =
                LogoutResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Logout successful")
                        .build();

        when(authServiceGrpcClient.logout(any(LogoutRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, String> logoutRequest =
                Map.of(
                        "accessToken", "token-xyz",
                        "refreshToken", "refresh-token-abc",
                        "sessionId", "session-123");

        webTestClient
                .post()
                .uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(logoutRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.success")
                .isEqualTo(true);

        verify(authServiceGrpcClient).logout(any(LogoutRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/verify-email should return 200 OK")
    void testVerifyEmail_shouldReturn200() {
        VerifyEmailResponse grpcResponse =
                VerifyEmailResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Email verified successfully")
                        .setAccountId("account-123")
                        .build();

        when(authServiceGrpcClient.verifyEmail(any(VerifyEmailRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, String> verifyRequest = Map.of("token", "verify-token-123");

        webTestClient
                .post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accountId")
                .isEqualTo("account-123");

        verify(authServiceGrpcClient).verifyEmail(any(VerifyEmailRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/resend-verification should return 200 OK")
    void testResendVerification_shouldReturn200() {
        ResendVerificationResponse grpcResponse =
                ResendVerificationResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Verification email resent")
                        .build();

        when(authServiceGrpcClient.resendVerification(any(ResendVerificationRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, String> resendRequest = Map.of("email", "test@example.com");

        webTestClient
                .post()
                .uri("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(resendRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.success")
                .isEqualTo(true);

        verify(authServiceGrpcClient).resendVerification(any(ResendVerificationRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/auth/.well-known/jwks.json should return JWKS keys")
    void testGetJwks_shouldReturn200() {
        GetJwksResponse grpcResponse =
                GetJwksResponse.newBuilder()
                        .addKeys(
                                JwkKeyProto.newBuilder()
                                        .setKty("OKP")
                                        .setCrv("Ed25519")
                                        .setKid("key-1")
                                        .setUse("sig")
                                        .setAlg("EdDSA")
                                        .setX("base64-encoded-public-key")
                                        .build())
                        .build();

        when(jwksCacheService.getJwksWithCache()).thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/auth/.well-known/jwks.json")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.keys")
                .isArray()
                .jsonPath("$.keys[0].kty")
                .isEqualTo("OKP")
                .jsonPath("$.keys[0].kid")
                .isEqualTo("key-1");

        verify(jwksCacheService).getJwksWithCache();
    }

    @Test
    @DisplayName("GET /api/v1/auth/jwks.json should return JWKS keys")
    void testGetJwksAlt_shouldReturn200() {
        GetJwksResponse grpcResponse =
                GetJwksResponse.newBuilder()
                        .addKeys(
                                JwkKeyProto.newBuilder()
                                        .setKty("OKP")
                                        .setCrv("Ed25519")
                                        .setKid("key-2")
                                        .setUse("sig")
                                        .setAlg("EdDSA")
                                        .setX("base64-encoded-public-key-2")
                                        .build())
                        .build();

        when(jwksCacheService.getJwksWithCache()).thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/auth/jwks.json")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.keys")
                .isArray()
                .jsonPath("$.keys[0].kid")
                .isEqualTo("key-2");

        verify(jwksCacheService).getJwksWithCache();
    }

    @Test
    @DisplayName("GET /api/v1/auth/accounts/{accountId} should return account details")
    void testGetAccount_shouldReturn200() {
        GetAccountResponse grpcResponse =
                GetAccountResponse.newBuilder()
                        .setAccount(
                                AccountProto.newBuilder()
                                        .setAccountId("account-123")
                                        .setEmail("test@example.com")
                                        .setRoleCode("RIDER")
                                        .setStatus("ACTIVE")
                                        .build())
                        .build();

        when(authServiceGrpcClient.getAccount(any(GetAccountRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/auth/accounts/account-123")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.account.accountId")
                .isEqualTo("account-123")
                .jsonPath("$.data.account.email")
                .isEqualTo("test@example.com");

        verify(authServiceGrpcClient).getAccount(any(GetAccountRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/auth/sessions should return sessions list")
    void testGetSessions_shouldReturn200() {
        GetSessionsResponse grpcResponse =
                GetSessionsResponse.newBuilder()
                        .addSessions(
                                SessionProto.newBuilder()
                                        .setSessionId("session-123")
                                        .setDeviceId("device-1")
                                        .setDeviceName("iPhone")
                                        .setIsCurrent(true)
                                        .build())
                        .build();

        when(authServiceGrpcClient.getSessions(any(GetSessionsRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/auth/sessions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.sessions[0].sessionId")
                .isEqualTo("session-123");

        verify(authServiceGrpcClient).getSessions(any(GetSessionsRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/sessions/{sessionId} should revoke session")
    void testRevokeSession_shouldReturn200() {
        RevokeSessionResponse grpcResponse =
                RevokeSessionResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Session revoked")
                        .build();

        when(authServiceGrpcClient.revokeSession(any(RevokeSessionRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, String> revokeRequest = Map.of("accountId", "account-123");

        webTestClient
                .method(HttpMethod.DELETE)
                .uri("/api/v1/auth/sessions/session-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(revokeRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.success")
                .isEqualTo(true);

        verify(authServiceGrpcClient).revokeSession(any(RevokeSessionRequest.class));
    }
}
