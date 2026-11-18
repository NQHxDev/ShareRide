package com.shareride.auth.grpc.mapper;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.common.grpc.auth.v1.AccountProto;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.GetSessionsResponse;
import com.shareride.common.grpc.auth.v1.HealthCheckResponse;
import com.shareride.common.grpc.auth.v1.JwkKeyProto;
import com.shareride.common.grpc.auth.v1.LoginResponse;
import com.shareride.common.grpc.auth.v1.LogoutRequest;
import com.shareride.common.grpc.auth.v1.LogoutResponse;
import com.shareride.common.grpc.auth.v1.RefreshTokenResponse;
import com.shareride.common.grpc.auth.v1.RegisterResponse;
import com.shareride.common.grpc.auth.v1.ResendVerificationResponse;
import com.shareride.common.grpc.auth.v1.RevokeSessionResponse;
import com.shareride.common.grpc.auth.v1.SessionProto;
import com.shareride.common.grpc.auth.v1.VerifyEmailResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mapper component for bidirectional conversion between internal DTOs and gRPC Protocol Buffer
 * messages.
 *
 * <p>This mapper extracts the mapping logic from AuthGrpcService to improve separation of concerns,
 * testability, and maintainability. It handles all 11 core authentication RPC method mappings
 * defined in auth_service.proto per Constitution Principle IV.
 */
@Component
public class AuthProtoMapper {

    // -------------------------------------------------------------------------
    // Register Request/Response Mapping
    // -------------------------------------------------------------------------

    public com.shareride.auth.dto.RegisterRequest toRegisterRequest(
            com.shareride.common.grpc.auth.v1.RegisterRequest protoRequest) {
        String roleCode = protoRequest.getRoleCode();
        if (roleCode != null && !roleCode.isEmpty() && !roleCode.startsWith("ROLE_")) {
            roleCode = "ROLE_" + roleCode;
        }

        return com.shareride.auth.dto.RegisterRequest.builder()
                .email(protoRequest.getEmail())
                .password(protoRequest.getPassword())
                .phoneNumber(protoRequest.getPhoneNumber())
                .roleCode(roleCode)
                .fullName(protoRequest.getDisplayName())
                .build();
    }

    public RegisterResponse toRegisterResponseProto(AccountResponse accountResponse) {
        RegisterResponse.Builder builder =
                RegisterResponse.newBuilder()
                        .setAccountId(accountResponse.getAccountId().toString())
                        .setEmail(
                                accountResponse.getEmail() != null
                                        ? accountResponse.getEmail()
                                        : "");

        if (accountResponse.getRoles() != null && !accountResponse.getRoles().isEmpty()) {
            builder.setRoleCode(accountResponse.getRoles().iterator().next());
        } else {
            builder.setRoleCode("");
        }

        if (accountResponse.getStatus() != null) {
            builder.setVerificationStatus(accountResponse.getStatus().name());
        } else {
            builder.setVerificationStatus("PENDING");
        }

        if (accountResponse.getCreatedAt() != null) {
            builder.setCreatedAt(accountResponse.getCreatedAt().toString());
        } else {
            builder.setCreatedAt("");
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Login Request/Response Mapping
    // -------------------------------------------------------------------------

    public com.shareride.auth.dto.LoginRequest toLoginRequest(
            com.shareride.common.grpc.auth.v1.LoginRequest protoRequest) {
        return com.shareride.auth.dto.LoginRequest.builder()
                .email(protoRequest.getEmail())
                .password(protoRequest.getPassword())
                .deviceId(protoRequest.getDeviceId())
                .deviceName(protoRequest.getDeviceName())
                .build();
    }

    public LoginResponse toLoginResponseProto(AuthResponse authResponse) {
        LoginResponse.Builder builder =
                LoginResponse.newBuilder()
                        .setAccessToken(authResponse.getAccessToken())
                        .setRefreshToken(authResponse.getRefreshToken())
                        .setTokenType(authResponse.getTokenType())
                        .setExpiresIn(
                                authResponse.getExpiresIn() != null
                                        ? authResponse.getExpiresIn().getEpochSecond()
                                        : 0)
                        .setSessionId(authResponse.getSessionId().toString());

        if (authResponse.getAccount() != null) {
            builder.setAccount(toAccountProto(authResponse.getAccount()));
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // RefreshToken Request/Response Mapping
    // -------------------------------------------------------------------------

    public String extractRefreshToken(
            com.shareride.common.grpc.auth.v1.RefreshTokenRequest protoRequest) {
        return protoRequest.getRefreshToken();
    }

    public String extractClientIp(
            com.shareride.common.grpc.auth.v1.RefreshTokenRequest protoRequest) {
        return protoRequest.getClientIp();
    }

    public String extractUserAgent(
            com.shareride.common.grpc.auth.v1.RefreshTokenRequest protoRequest) {
        return protoRequest.getUserAgent();
    }

    public RefreshTokenResponse toRefreshTokenResponseProto(AuthResponse authResponse) {
        return RefreshTokenResponse.newBuilder()
                .setAccessToken(authResponse.getAccessToken())
                .setRefreshToken(authResponse.getRefreshToken())
                .setTokenType(authResponse.getTokenType())
                .setExpiresIn(
                        authResponse.getExpiresIn() != null
                                ? authResponse.getExpiresIn().getEpochSecond()
                                : 0)
                .build();
    }

    // -------------------------------------------------------------------------
    // Logout Request/Response Mapping
    // -------------------------------------------------------------------------

    public String extractAccessToken(LogoutRequest protoRequest) {
        return protoRequest.getAccessToken();
    }

    public String extractRefreshTokenFromLogout(LogoutRequest protoRequest) {
        return protoRequest.getRefreshToken();
    }

    public LogoutResponse toLogoutResponseProto(boolean success, String message) {
        return LogoutResponse.newBuilder().setSuccess(success).setMessage(message).build();
    }

    // -------------------------------------------------------------------------
    // VerifyEmail Request/Response Mapping
    // -------------------------------------------------------------------------

    public com.shareride.auth.dto.VerifyEmailRequest toVerifyEmailRequest(
            com.shareride.common.grpc.auth.v1.VerifyEmailRequest protoRequest) {
        return com.shareride.auth.dto.VerifyEmailRequest.builder()
                .code(protoRequest.getToken())
                .build();
    }

    public VerifyEmailResponse toVerifyEmailResponseProto(
            boolean success, String message, UUID accountId) {
        VerifyEmailResponse.Builder builder =
                VerifyEmailResponse.newBuilder().setSuccess(success).setMessage(message);

        if (accountId != null) {
            builder.setAccountId(accountId.toString());
        } else {
            builder.setAccountId("");
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // ResendVerification Request/Response Mapping
    // -------------------------------------------------------------------------

    public com.shareride.auth.dto.ResendVerificationRequest toResendVerificationRequest(
            com.shareride.common.grpc.auth.v1.ResendVerificationRequest protoRequest) {
        return com.shareride.auth.dto.ResendVerificationRequest.builder()
                .email(protoRequest.getEmail())
                .build();
    }

    public ResendVerificationResponse toResendVerificationResponseProto(
            boolean success, String message) {
        return ResendVerificationResponse.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build();
    }

    // -------------------------------------------------------------------------
    // GetJwks Response Mapping
    // -------------------------------------------------------------------------

    public GetJwksResponse toGetJwksResponseProto(Map<String, Object> jwks) {
        GetJwksResponse.Builder responseBuilder = GetJwksResponse.newBuilder();

        Object keysObj = jwks.get("keys");
        if (keysObj instanceof List) {
            List<?> keys = (List<?>) keysObj;
            for (Object keyObj : keys) {
                if (keyObj instanceof Map) {
                    Map<?, ?> key = (Map<?, ?>) keyObj;
                    JwkKeyProto.Builder keyBuilder = JwkKeyProto.newBuilder();

                    if (key.get("kty") != null) {
                        keyBuilder.setKty(key.get("kty").toString());
                    }
                    if (key.get("crv") != null) {
                        keyBuilder.setCrv(key.get("crv").toString());
                    }
                    if (key.get("x") != null) {
                        keyBuilder.setX(key.get("x").toString());
                    }
                    if (key.get("kid") != null) {
                        keyBuilder.setKid(key.get("kid").toString());
                    }
                    if (key.get("use") != null) {
                        keyBuilder.setUse(key.get("use").toString());
                    }
                    if (key.get("alg") != null) {
                        keyBuilder.setAlg(key.get("alg").toString());
                    }

                    responseBuilder.addKeys(keyBuilder.build());
                }
            }
        }

        return responseBuilder.build();
    }

    // -------------------------------------------------------------------------
    // GetAccount Request/Response Mapping
    // -------------------------------------------------------------------------

    public UUID extractAccountId(com.shareride.common.grpc.auth.v1.GetAccountRequest protoRequest) {
        return UUID.fromString(protoRequest.getAccountId());
    }

    public GetAccountResponse toGetAccountResponseProto(AccountResponse accountResponse) {
        return GetAccountResponse.newBuilder().setAccount(toAccountProto(accountResponse)).build();
    }

    // -------------------------------------------------------------------------
    // GetSessions Request/Response Mapping
    // -------------------------------------------------------------------------

    public UUID extractAccountIdFromGetSessions(
            com.shareride.common.grpc.auth.v1.GetSessionsRequest protoRequest) {
        return UUID.fromString(protoRequest.getAccountId());
    }

    public GetSessionsResponse toGetSessionsResponseProto(List<SessionResponse> sessions) {
        GetSessionsResponse.Builder responseBuilder = GetSessionsResponse.newBuilder();

        if (sessions != null) {
            for (SessionResponse session : sessions) {
                responseBuilder.addSessions(toSessionProto(session));
            }
        }

        return responseBuilder.build();
    }

    // -------------------------------------------------------------------------
    // RevokeSession Request/Response Mapping
    // -------------------------------------------------------------------------

    public UUID extractAccountIdFromRevokeSession(
            com.shareride.common.grpc.auth.v1.RevokeSessionRequest protoRequest) {
        return UUID.fromString(protoRequest.getAccountId());
    }

    public UUID extractSessionId(
            com.shareride.common.grpc.auth.v1.RevokeSessionRequest protoRequest) {
        return UUID.fromString(protoRequest.getSessionId());
    }

    public RevokeSessionResponse toRevokeSessionResponseProto(boolean success, String message) {
        return RevokeSessionResponse.newBuilder().setSuccess(success).setMessage(message).build();
    }

    // -------------------------------------------------------------------------
    // HealthCheck Response Mapping
    // -------------------------------------------------------------------------

    public HealthCheckResponse toHealthCheckResponseProto(
            HealthCheckResponse.ServingStatus status, String message, long timestamp) {
        return HealthCheckResponse.newBuilder()
                .setStatus(status)
                .setMessage(message)
                .setTimestamp(timestamp)
                .build();
    }

    // -------------------------------------------------------------------------
    // Private Helper Methods
    // -------------------------------------------------------------------------

    private AccountProto toAccountProto(AccountResponse accountResponse) {
        AccountProto.Builder builder =
                AccountProto.newBuilder()
                        .setAccountId(accountResponse.getAccountId().toString())
                        .setEmail(
                                accountResponse.getEmail() != null
                                        ? accountResponse.getEmail()
                                        : "")
                        .setStatus(
                                accountResponse.getStatus() != null
                                        ? accountResponse.getStatus().name()
                                        : "");

        if (accountResponse.getPhoneNumber() != null) {
            builder.setPhoneNumber(accountResponse.getPhoneNumber());
        }

        if (accountResponse.getRoles() != null && !accountResponse.getRoles().isEmpty()) {
            builder.setRoleCode(accountResponse.getRoles().iterator().next());
            builder.addAllRoles(accountResponse.getRoles());
        }

        if (accountResponse.getPermissions() != null) {
            builder.addAllPermissions(accountResponse.getPermissions());
        }

        if (accountResponse.getCreatedAt() != null) {
            builder.setCreatedAt(accountResponse.getCreatedAt().toString());
        }

        if (accountResponse.getUpdatedAt() != null) {
            builder.setUpdatedAt(accountResponse.getUpdatedAt().toString());
        }

        return builder.build();
    }

    private SessionProto toSessionProto(SessionResponse sessionResponse) {
        SessionProto.Builder builder =
                SessionProto.newBuilder()
                        .setSessionId(sessionResponse.getSessionId().toString())
                        .setCreatedAt(
                                sessionResponse.getCreatedAt() != null
                                        ? sessionResponse.getCreatedAt().toString()
                                        : "");

        if (sessionResponse.getDeviceId() != null) {
            builder.setDeviceId(sessionResponse.getDeviceId());
        }

        if (sessionResponse.getDeviceName() != null) {
            builder.setDeviceName(sessionResponse.getDeviceName());
        }

        if (sessionResponse.getIpAddress() != null) {
            builder.setClientIp(sessionResponse.getIpAddress());
        }

        if (sessionResponse.getLastActiveAt() != null) {
            builder.setLastActiveAt(sessionResponse.getLastActiveAt().toString());
        }

        if (sessionResponse.getExpiresAt() != null) {
            builder.setExpiresAt(sessionResponse.getExpiresAt().toString());
        }

        return builder.build();
    }
}
