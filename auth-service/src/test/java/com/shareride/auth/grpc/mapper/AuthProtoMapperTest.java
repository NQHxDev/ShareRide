package com.shareride.auth.grpc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.dto.AccountResponseDTO;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.RegisterRequestDTO;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.common.grpc.auth.v1.AccountProto;
import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.GetSessionsRequest;
import com.shareride.common.grpc.auth.v1.GetSessionsResponse;
import com.shareride.common.grpc.auth.v1.HealthCheckResponse;
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
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AuthProtoMapper validating bidirectional conversion between internal DTOs and gRPC
 * Protocol Buffer messages. Tests all 11 RPC method mappings per data-model.md Section 4.1.
 *
 * <p>Per T027 (Phase 4 User Story 2) and Constitution Principle IV.
 */
@ExtendWith(MockitoExtension.class)
class AuthProtoMapperTest {

    private AuthProtoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AuthProtoMapper();
    }

    // -------------------------------------------------------------------------
    // Register Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toRegisterRequest: Should map proto RegisterRequest to internal DTO")
    void testToRegisterRequest() {
        RegisterRequest protoRequest =
                RegisterRequest.newBuilder()
                        .setEmail("test@example.com")
                        .setPassword("SecurePass123")
                        .setConfirmPassword("SecurePass123")
                        .build();

        RegisterRequestDTO dto = mapper.toRegisterRequest(protoRequest);

        assertThat(dto).isNotNull();
        assertThat(dto.getEmail()).isEqualTo("test@example.com");
        assertThat(dto.getPassword()).isEqualTo("SecurePass123");
    }

    @Test
    @DisplayName("toRegisterResponseProto: Should map AccountResponse to proto RegisterResponse")
    void testToRegisterResponseProto() {
        UUID accountId = UuidUtils.v7();
        Instant createdAt = Instant.now();

        AccountResponseDTO accountResponse =
                AccountResponseDTO.builder()
                        .accountId(accountId)
                        .email("test@example.com")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .roles(Set.of("ROLE_RIDER"))
                        .createdAt(createdAt)
                        .build();

        RegisterResponse proto = mapper.toRegisterResponseProto(accountResponse);

        assertThat(proto).isNotNull();
        assertThat(proto.getAccountId()).isEqualTo(accountId.toString());
        assertThat(proto.getEmail()).isEqualTo("test@example.com");
        assertThat(proto.getVerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(proto.getCreatedAt()).isEqualTo(createdAt.toString());
    }

    @Test
    @DisplayName(
            "toRegisterResponseProto: Should handle AccountResponse with null/empty optional"
                    + " fields")
    void testToRegisterResponseProtoWithNulls() {
        UUID accountId = UuidUtils.v7();

        AccountResponseDTO accountResponse =
                AccountResponseDTO.builder()
                        .accountId(accountId)
                        .email(null)
                        .status(null)
                        .roles(Collections.emptySet())
                        .createdAt(null)
                        .build();

        RegisterResponse proto = mapper.toRegisterResponseProto(accountResponse);

        assertThat(proto).isNotNull();
        assertThat(proto.getAccountId()).isEqualTo(accountId.toString());
        assertThat(proto.getEmail()).isEmpty();
        assertThat(proto.getRoleCode()).isEmpty();
        assertThat(proto.getVerificationStatus()).isEqualTo("PENDING");
        assertThat(proto.getCreatedAt()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Login Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toLoginRequest: Should map proto LoginRequest to internal DTO")
    void testToLoginRequest() {
        LoginRequest protoRequest =
                LoginRequest.newBuilder()
                        .setEmail("test@example.com")
                        .setPassword("SecurePass123")
                        .setDeviceId("device-001")
                        .setDeviceName("iPhone 15 Pro")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("ShareRide-iOS/1.0")
                        .build();

        com.shareride.auth.dto.LoginRequest dto = mapper.toLoginRequest(protoRequest);

        assertThat(dto).isNotNull();
        assertThat(dto.getEmail()).isEqualTo("test@example.com");
        assertThat(dto.getPassword()).isEqualTo("SecurePass123");
        assertThat(dto.getDeviceId()).isEqualTo("device-001");
        assertThat(dto.getDeviceName()).isEqualTo("iPhone 15 Pro");
    }

    @Test
    @DisplayName("toLoginResponseProto: Should map AuthResponse to proto LoginResponse")
    void testToLoginResponseProto() {
        UUID accountId = UuidUtils.v7();
        UUID sessionId = UuidUtils.v7();
        Instant expiresIn = Instant.now().plusSeconds(3600);

        AccountResponseDTO account =
                AccountResponseDTO.builder()
                        .accountId(accountId)
                        .email("test@example.com")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        AuthResponse authResponse =
                AuthResponse.builder()
                        .accessToken("mock.access.token")
                        .refreshToken("mock.refresh.token")
                        .tokenType("Bearer")
                        .expiresIn(expiresIn)
                        .sessionId(sessionId)
                        .account(account)
                        .build();

        LoginResponse proto = mapper.toLoginResponseProto(authResponse);

        assertThat(proto).isNotNull();
        assertThat(proto.getAccessToken()).isEqualTo("mock.access.token");
        assertThat(proto.getRefreshToken()).isEqualTo("mock.refresh.token");
        assertThat(proto.getTokenType()).isEqualTo("Bearer");
        assertThat(proto.getExpiresIn()).isEqualTo(expiresIn.getEpochSecond());
        assertThat(proto.getSessionId()).isEqualTo(sessionId.toString());
        assertThat(proto.hasAccount()).isTrue();
        assertThat(proto.getAccount().getAccountId()).isEqualTo(accountId.toString());
        assertThat(proto.getAccount().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("toLoginResponseProto: Should handle AuthResponse with null expiresIn")
    void testToLoginResponseProtoWithNullExpiry() {
        UUID sessionId = UuidUtils.v7();

        AuthResponse authResponse =
                AuthResponse.builder()
                        .accessToken("mock.access.token")
                        .refreshToken("mock.refresh.token")
                        .tokenType("Bearer")
                        .expiresIn(null)
                        .sessionId(sessionId)
                        .account(null)
                        .build();

        LoginResponse proto = mapper.toLoginResponseProto(authResponse);

        assertThat(proto).isNotNull();
        assertThat(proto.getExpiresIn()).isEqualTo(0);
        assertThat(proto.hasAccount()).isFalse();
    }

    // -------------------------------------------------------------------------
    // RefreshToken Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractRefreshToken: Should extract refresh token from proto request")
    void testExtractRefreshToken() {
        RefreshTokenRequest protoRequest =
                RefreshTokenRequest.newBuilder()
                        .setRefreshToken("mock.refresh.token")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("ShareRide-iOS/1.0")
                        .build();

        String refreshToken = mapper.extractRefreshToken(protoRequest);

        assertThat(refreshToken).isEqualTo("mock.refresh.token");
    }

    @Test
    @DisplayName("extractClientIp: Should extract client IP from proto request")
    void testExtractClientIp() {
        RefreshTokenRequest protoRequest =
                RefreshTokenRequest.newBuilder()
                        .setRefreshToken("mock.refresh.token")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("ShareRide-iOS/1.0")
                        .build();

        String clientIp = mapper.extractClientIp(protoRequest);

        assertThat(clientIp).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("extractUserAgent: Should extract user agent from proto request")
    void testExtractUserAgent() {
        RefreshTokenRequest protoRequest =
                RefreshTokenRequest.newBuilder()
                        .setRefreshToken("mock.refresh.token")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("ShareRide-iOS/1.0")
                        .build();

        String userAgent = mapper.extractUserAgent(protoRequest);

        assertThat(userAgent).isEqualTo("ShareRide-iOS/1.0");
    }

    @Test
    @DisplayName(
            "toRefreshTokenResponseProto: Should map AuthResponse to proto RefreshTokenResponse")
    void testToRefreshTokenResponseProto() {
        Instant expiresIn = Instant.now().plusSeconds(3600);

        AuthResponse authResponse =
                AuthResponse.builder()
                        .accessToken("new.access.token")
                        .refreshToken("new.refresh.token")
                        .tokenType("Bearer")
                        .expiresIn(expiresIn)
                        .build();

        RefreshTokenResponse proto = mapper.toRefreshTokenResponseProto(authResponse);

        assertThat(proto).isNotNull();
        assertThat(proto.getAccessToken()).isEqualTo("new.access.token");
        assertThat(proto.getRefreshToken()).isEqualTo("new.refresh.token");
        assertThat(proto.getTokenType()).isEqualTo("Bearer");
        assertThat(proto.getExpiresIn()).isEqualTo(expiresIn.getEpochSecond());
    }

    // -------------------------------------------------------------------------
    // Logout Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractAccessToken: Should extract access token from LogoutRequest")
    void testExtractAccessToken() {
        LogoutRequest protoRequest =
                LogoutRequest.newBuilder()
                        .setAccessToken("mock.access.token")
                        .setRefreshToken("mock.refresh.token")
                        .setSessionId(UuidUtils.v7AsString())
                        .build();

        String accessToken = mapper.extractAccessToken(protoRequest);

        assertThat(accessToken).isEqualTo("mock.access.token");
    }

    @Test
    @DisplayName("extractRefreshTokenFromLogout: Should extract refresh token from LogoutRequest")
    void testExtractRefreshTokenFromLogout() {
        LogoutRequest protoRequest =
                LogoutRequest.newBuilder()
                        .setAccessToken("mock.access.token")
                        .setRefreshToken("mock.refresh.token")
                        .setSessionId(UuidUtils.v7AsString())
                        .build();

        String refreshToken = mapper.extractRefreshTokenFromLogout(protoRequest);

        assertThat(refreshToken).isEqualTo("mock.refresh.token");
    }

    @Test
    @DisplayName("toLogoutResponseProto: Should create LogoutResponse with success and message")
    void testToLogoutResponseProto() {
        LogoutResponse proto = mapper.toLogoutResponseProto(true, "Logout successful");

        assertThat(proto).isNotNull();
        assertThat(proto.getSuccess()).isTrue();
        assertThat(proto.getMessage()).isEqualTo("Logout successful");
    }

    // -------------------------------------------------------------------------
    // VerifyEmail Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toVerifyEmailRequest: Should map proto VerifyEmailRequest to internal DTO")
    void testToVerifyEmailRequest() {
        VerifyEmailRequest protoRequest =
                VerifyEmailRequest.newBuilder().setToken("verification-token-123").build();

        com.shareride.auth.dto.VerifyEmailRequest dto = mapper.toVerifyEmailRequest(protoRequest);

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("verification-token-123");
    }

    @Test
    @DisplayName("toVerifyEmailResponseProto: Should create VerifyEmailResponse with account ID")
    void testToVerifyEmailResponseProto() {
        UUID accountId = UuidUtils.v7();

        VerifyEmailResponse proto =
                mapper.toVerifyEmailResponseProto(true, "Email verified successfully", accountId);

        assertThat(proto).isNotNull();
        assertThat(proto.getSuccess()).isTrue();
        assertThat(proto.getMessage()).isEqualTo("Email verified successfully");
        assertThat(proto.getAccountId()).isEqualTo(accountId.toString());
    }

    @Test
    @DisplayName("toVerifyEmailResponseProto: Should handle null account ID")
    void testToVerifyEmailResponseProtoWithNullAccountId() {
        VerifyEmailResponse proto =
                mapper.toVerifyEmailResponseProto(false, "Verification failed", null);

        assertThat(proto).isNotNull();
        assertThat(proto.getSuccess()).isFalse();
        assertThat(proto.getMessage()).isEqualTo("Verification failed");
        assertThat(proto.getAccountId()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // ResendVerification Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "toResendVerificationRequest: Should map proto ResendVerificationRequest to internal"
                    + " DTO")
    void testToResendVerificationRequest() {
        ResendVerificationRequest protoRequest =
                ResendVerificationRequest.newBuilder().setEmail("test@example.com").build();

        com.shareride.auth.dto.ResendVerificationRequest dto =
                mapper.toResendVerificationRequest(protoRequest);

        assertThat(dto).isNotNull();
        assertThat(dto.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName(
            "toResendVerificationResponseProto: Should create ResendVerificationResponse with"
                    + " success flag")
    void testToResendVerificationResponseProto() {
        ResendVerificationResponse proto =
                mapper.toResendVerificationResponseProto(true, "Verification email sent");

        assertThat(proto).isNotNull();
        assertThat(proto.getSuccess()).isTrue();
        assertThat(proto.getMessage()).isEqualTo("Verification email sent");
    }

    // -------------------------------------------------------------------------
    // GetJwks Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toGetJwksResponseProto: Should map JWKS map to proto GetJwksResponse")
    void testToGetJwksResponseProto() {
        Map<String, Object> jwks =
                Map.of(
                        "keys",
                        List.of(
                                Map.of(
                                        "kty", "OKP",
                                        "crv", "Ed25519",
                                        "x", "mockBase64Key",
                                        "kid", "key-001",
                                        "use", "sig",
                                        "alg", "EdDSA")));

        GetJwksResponse proto = mapper.toGetJwksResponseProto(jwks);

        assertThat(proto).isNotNull();
        assertThat(proto.getKeysList()).hasSize(1);

        JwkKeyProto key = proto.getKeys(0);
        assertThat(key.getKty()).isEqualTo("OKP");
        assertThat(key.getCrv()).isEqualTo("Ed25519");
        assertThat(key.getX()).isEqualTo("mockBase64Key");
        assertThat(key.getKid()).isEqualTo("key-001");
        assertThat(key.getUse()).isEqualTo("sig");
        assertThat(key.getAlg()).isEqualTo("EdDSA");
    }

    @Test
    @DisplayName("toGetJwksResponseProto: Should handle multiple keys")
    void testToGetJwksResponseProtoWithMultipleKeys() {
        Map<String, Object> jwks =
                Map.of(
                        "keys",
                        List.of(
                                Map.of(
                                        "kty", "OKP",
                                        "crv", "Ed25519",
                                        "x", "key1Base64",
                                        "kid", "key-001",
                                        "use", "sig",
                                        "alg", "EdDSA"),
                                Map.of(
                                        "kty", "OKP",
                                        "crv", "Ed25519",
                                        "x", "key2Base64",
                                        "kid", "key-002",
                                        "use", "sig",
                                        "alg", "EdDSA")));

        GetJwksResponse proto = mapper.toGetJwksResponseProto(jwks);

        assertThat(proto).isNotNull();
        assertThat(proto.getKeysList()).hasSize(2);
        assertThat(proto.getKeys(0).getKid()).isEqualTo("key-001");
        assertThat(proto.getKeys(1).getKid()).isEqualTo("key-002");
    }

    @Test
    @DisplayName("toGetJwksResponseProto: Should handle empty JWKS")
    void testToGetJwksResponseProtoWithEmptyKeys() {
        Map<String, Object> jwks = Map.of("keys", Collections.emptyList());

        GetJwksResponse proto = mapper.toGetJwksResponseProto(jwks);

        assertThat(proto).isNotNull();
        assertThat(proto.getKeysList()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // GetAccount Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractAccountId: Should extract and parse account UUID from GetAccountRequest")
    void testExtractAccountId() {
        UUID accountId = UuidUtils.v7();
        GetAccountRequest protoRequest =
                GetAccountRequest.newBuilder().setAccountId(accountId.toString()).build();

        UUID extractedId = mapper.extractAccountId(protoRequest);

        assertThat(extractedId).isEqualTo(accountId);
    }

    @Test
    @DisplayName("toGetAccountResponseProto: Should map AccountResponse to proto with AccountProto")
    void testToGetAccountResponseProto() {
        UUID accountId = UuidUtils.v7();
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();

        AccountResponseDTO accountResponse =
                AccountResponseDTO.builder()
                        .accountId(accountId)
                        .email("test@example.com")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER", "ROLE_DRIVER"))
                        .permissions(Set.of("READ:PROFILE", "UPDATE:PROFILE"))
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .build();

        GetAccountResponse proto = mapper.toGetAccountResponseProto(accountResponse);

        assertThat(proto).isNotNull();
        assertThat(proto.hasAccount()).isTrue();

        AccountProto account = proto.getAccount();
        assertThat(account.getAccountId()).isEqualTo(accountId.toString());
        assertThat(account.getEmail()).isEqualTo("test@example.com");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(account.getRolesList()).containsExactlyInAnyOrder("ROLE_RIDER", "ROLE_DRIVER");
        assertThat(account.getPermissionsList())
                .containsExactlyInAnyOrder("READ:PROFILE", "UPDATE:PROFILE");
        assertThat(account.getCreatedAt()).isEqualTo(createdAt.toString());
        assertThat(account.getUpdatedAt()).isEqualTo(updatedAt.toString());
    }

    // -------------------------------------------------------------------------
    // GetSessions Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "extractAccountIdFromGetSessions: Should extract account UUID from GetSessionsRequest")
    void testExtractAccountIdFromGetSessions() {
        UUID accountId = UuidUtils.v7();
        GetSessionsRequest protoRequest =
                GetSessionsRequest.newBuilder().setAccountId(accountId.toString()).build();

        UUID extractedId = mapper.extractAccountIdFromGetSessions(protoRequest);

        assertThat(extractedId).isEqualTo(accountId);
    }

    @Test
    @DisplayName(
            "toGetSessionsResponseProto: Should map SessionResponse list to proto with"
                    + " SessionProto")
    void testToGetSessionsResponseProto() {
        UUID sessionId1 = UuidUtils.v7();
        UUID sessionId2 = UuidUtils.v7();
        Instant now = Instant.now();

        SessionResponse session1 =
                SessionResponse.builder()
                        .sessionId(sessionId1)
                        .deviceId("device-001")
                        .deviceName("iPhone 15 Pro")
                        .ipAddress("192.168.1.100")
                        .createdAt(now)
                        .lastActiveAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .build();

        SessionResponse session2 =
                SessionResponse.builder()
                        .sessionId(sessionId2)
                        .deviceId("device-002")
                        .deviceName("MacBook Pro")
                        .ipAddress("192.168.1.101")
                        .createdAt(now)
                        .lastActiveAt(now)
                        .expiresAt(now.plusSeconds(7200))
                        .build();

        List<SessionResponse> sessions = List.of(session1, session2);

        GetSessionsResponse proto = mapper.toGetSessionsResponseProto(sessions);

        assertThat(proto).isNotNull();
        assertThat(proto.getSessionsList()).hasSize(2);

        SessionProto protoSession1 = proto.getSessions(0);
        assertThat(protoSession1.getSessionId()).isEqualTo(sessionId1.toString());
        assertThat(protoSession1.getDeviceId()).isEqualTo("device-001");
        assertThat(protoSession1.getDeviceName()).isEqualTo("iPhone 15 Pro");
        assertThat(protoSession1.getClientIp()).isEqualTo("192.168.1.100");

        SessionProto protoSession2 = proto.getSessions(1);
        assertThat(protoSession2.getSessionId()).isEqualTo(sessionId2.toString());
        assertThat(protoSession2.getDeviceId()).isEqualTo("device-002");
    }

    @Test
    @DisplayName("toGetSessionsResponseProto: Should handle null session list")
    void testToGetSessionsResponseProtoWithNull() {
        GetSessionsResponse proto = mapper.toGetSessionsResponseProto(null);

        assertThat(proto).isNotNull();
        assertThat(proto.getSessionsList()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // RevokeSession Request/Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "extractAccountIdFromRevokeSession: Should extract account UUID from"
                    + " RevokeSessionRequest")
    void testExtractAccountIdFromRevokeSession() {
        UUID accountId = UuidUtils.v7();
        UUID sessionId = UuidUtils.v7();

        RevokeSessionRequest protoRequest =
                RevokeSessionRequest.newBuilder()
                        .setAccountId(accountId.toString())
                        .setSessionId(sessionId.toString())
                        .build();

        UUID extractedAccountId = mapper.extractAccountIdFromRevokeSession(protoRequest);

        assertThat(extractedAccountId).isEqualTo(accountId);
    }

    @Test
    @DisplayName("extractSessionId: Should extract session UUID from RevokeSessionRequest")
    void testExtractSessionId() {
        UUID accountId = UuidUtils.v7();
        UUID sessionId = UuidUtils.v7();

        RevokeSessionRequest protoRequest =
                RevokeSessionRequest.newBuilder()
                        .setAccountId(accountId.toString())
                        .setSessionId(sessionId.toString())
                        .build();

        UUID extractedSessionId = mapper.extractSessionId(protoRequest);

        assertThat(extractedSessionId).isEqualTo(sessionId);
    }

    @Test
    @DisplayName(
            "toRevokeSessionResponseProto: Should create RevokeSessionResponse with success flag")
    void testToRevokeSessionResponseProto() {
        RevokeSessionResponse proto =
                mapper.toRevokeSessionResponseProto(true, "Session revoked successfully");

        assertThat(proto).isNotNull();
        assertThat(proto.getSuccess()).isTrue();
        assertThat(proto.getMessage()).isEqualTo("Session revoked successfully");
    }

    // -------------------------------------------------------------------------
    // HealthCheck Response Mapping Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "toHealthCheckResponseProto: Should create HealthCheckResponse with SERVING status")
    void testToHealthCheckResponseProto() {
        long timestamp = Instant.now().toEpochMilli();

        HealthCheckResponse proto =
                mapper.toHealthCheckResponseProto(
                        HealthCheckResponse.ServingStatus.SERVING,
                        "Auth service is healthy",
                        timestamp);

        assertThat(proto).isNotNull();
        assertThat(proto.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        assertThat(proto.getMessage()).isEqualTo("Auth service is healthy");
        assertThat(proto.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName(
            "toHealthCheckResponseProto: Should create HealthCheckResponse with NOT_SERVING status")
    void testToHealthCheckResponseProtoNotServing() {
        long timestamp = Instant.now().toEpochMilli();

        HealthCheckResponse proto =
                mapper.toHealthCheckResponseProto(
                        HealthCheckResponse.ServingStatus.NOT_SERVING,
                        "Auth service is unhealthy",
                        timestamp);

        assertThat(proto).isNotNull();
        assertThat(proto.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.NOT_SERVING);
        assertThat(proto.getMessage()).isEqualTo("Auth service is unhealthy");
    }
}
