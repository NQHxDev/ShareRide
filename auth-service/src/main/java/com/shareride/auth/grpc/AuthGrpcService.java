package com.shareride.auth.grpc;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.grpc.mapper.AuthProtoMapper;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.AccountVerificationService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.common.communication.grpc.server.GrpcService;
import com.shareride.common.grpc.auth.v1.AuthServiceGrpc;
import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.GetSessionsRequest;
import com.shareride.common.grpc.auth.v1.GetSessionsResponse;
import com.shareride.common.grpc.auth.v1.HealthCheckRequest;
import com.shareride.common.grpc.auth.v1.HealthCheckResponse;
import com.shareride.common.grpc.auth.v1.LogoutRequest;
import com.shareride.common.grpc.auth.v1.LogoutResponse;
import com.shareride.common.grpc.auth.v1.RefreshTokenResponse;
import com.shareride.common.grpc.auth.v1.RegisterResponse;
import com.shareride.common.grpc.auth.v1.RevokeSessionRequest;
import com.shareride.common.grpc.auth.v1.RevokeSessionResponse;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service implementation for authentication and account management operations. Implements all
 * 11 RPC methods defined in auth_service.proto per Constitution Principle IV (synchronous gRPC for
 * inter-service communication).
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Account lifecycle: Register, VerifyEmail, ResendVerification, GetAccount
 *   <li>Authentication: Login, RefreshToken, Logout, GetJwks
 *   <li>Session management: GetSessions, RevokeSession
 *   <li>Health checking: HealthCheck
 * </ul>
 *
 * <p>All protocol buffer mapping logic is delegated to {@link AuthProtoMapper} for improved
 * testability and separation of concerns.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;
    private final AccountManagementService accountManagementService;
    private final SessionManagementService sessionManagementService;
    private final AccountVerificationService accountVerificationService;
    private final AuthProtoMapper mapper;

    @Override
    public void register(
            com.shareride.common.grpc.auth.v1.RegisterRequest request,
            StreamObserver<RegisterResponse> responseObserver) {
        log.debug("gRPC Register request received for email: {}", request.getEmail());

        try {
            com.shareride.auth.dto.RegisterRequest registerRequest =
                    mapper.toRegisterRequest(request);
            AccountResponse response = authService.register(registerRequest);
            RegisterResponse grpcResponse = mapper.toRegisterResponseProto(response);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info(
                    "gRPC Register completed successfully for account: {}",
                    response.getAccountId());
        } catch (Exception e) {
            log.error("gRPC Register failed for email: {}", request.getEmail(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void login(
            com.shareride.common.grpc.auth.v1.LoginRequest request,
            StreamObserver<com.shareride.common.grpc.auth.v1.LoginResponse> responseObserver) {
        log.debug("gRPC Login request received for email: {}", request.getEmail());

        try {
            com.shareride.auth.dto.LoginRequest loginRequest = mapper.toLoginRequest(request);
            AuthResponse response =
                    authService.login(loginRequest, request.getClientIp(), request.getUserAgent());
            com.shareride.common.grpc.auth.v1.LoginResponse grpcResponse =
                    mapper.toLoginResponseProto(response);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC Login completed successfully for email: {}", request.getEmail());
        } catch (Exception e) {
            log.error("gRPC Login failed for email: {}", request.getEmail(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void refreshToken(
            com.shareride.common.grpc.auth.v1.RefreshTokenRequest request,
            StreamObserver<RefreshTokenResponse> responseObserver) {
        log.debug("gRPC RefreshToken request received");

        try {
            String refreshToken = mapper.extractRefreshToken(request);
            String clientIp = mapper.extractClientIp(request);
            String userAgent = mapper.extractUserAgent(request);

            AuthResponse response = authService.refreshToken(refreshToken, clientIp, userAgent);
            RefreshTokenResponse grpcResponse = mapper.toRefreshTokenResponseProto(response);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC RefreshToken completed successfully");
        } catch (Exception e) {
            log.error("gRPC RefreshToken failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        log.debug("gRPC Logout request received");

        try {
            String accessToken = mapper.extractAccessToken(request);
            String refreshToken = mapper.extractRefreshTokenFromLogout(request);
            authService.logout(accessToken, refreshToken);

            LogoutResponse grpcResponse = mapper.toLogoutResponseProto(true, "Logout successful");

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC Logout completed successfully");
        } catch (Exception e) {
            log.error("gRPC Logout failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void verifyEmail(
            com.shareride.common.grpc.auth.v1.VerifyEmailRequest request,
            StreamObserver<com.shareride.common.grpc.auth.v1.VerifyEmailResponse>
                    responseObserver) {
        log.debug("gRPC VerifyEmail request received");

        try {
            com.shareride.auth.dto.VerifyEmailRequest verifyRequest =
                    mapper.toVerifyEmailRequest(request);
            AccountResponse accountResponse = accountVerificationService.verifyEmail(verifyRequest);

            com.shareride.common.grpc.auth.v1.VerifyEmailResponse grpcResponse =
                    mapper.toVerifyEmailResponseProto(
                            true, "Email verified successfully", accountResponse.getAccountId());

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info(
                    "gRPC VerifyEmail completed successfully for account: {}",
                    accountResponse.getAccountId());
        } catch (Exception e) {
            log.error("gRPC VerifyEmail failed", e);
            com.shareride.common.grpc.auth.v1.VerifyEmailResponse errorResponse =
                    mapper.toVerifyEmailResponseProto(
                            false, "Email verification failed: " + e.getMessage(), null);
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void resendVerification(
            com.shareride.common.grpc.auth.v1.ResendVerificationRequest request,
            StreamObserver<com.shareride.common.grpc.auth.v1.ResendVerificationResponse>
                    responseObserver) {
        log.debug("gRPC ResendVerification request received for email: {}", request.getEmail());

        try {
            com.shareride.auth.dto.ResendVerificationRequest resendRequest =
                    mapper.toResendVerificationRequest(request);
            com.shareride.auth.dto.ResendVerificationResponse response =
                    accountVerificationService.resendVerification(resendRequest);

            String message =
                    response.getMessage() != null
                            ? response.getMessage()
                            : "Verification email sent successfully";
            com.shareride.common.grpc.auth.v1.ResendVerificationResponse grpcResponse =
                    mapper.toResendVerificationResponseProto(true, message);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info(
                    "gRPC ResendVerification completed successfully for email: {}",
                    request.getEmail());
        } catch (Exception e) {
            log.error("gRPC ResendVerification failed for email: {}", request.getEmail(), e);
            com.shareride.common.grpc.auth.v1.ResendVerificationResponse errorResponse =
                    mapper.toResendVerificationResponseProto(
                            false, "Failed to resend verification: " + e.getMessage());
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getJwks(GetJwksRequest request, StreamObserver<GetJwksResponse> responseObserver) {
        log.debug("gRPC GetJwks request received");

        try {
            Map<String, Object> jwks = authService.getJwks();
            GetJwksResponse grpcResponse = mapper.toGetJwksResponseProto(jwks);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC GetJwks completed successfully");
        } catch (Exception e) {
            log.error("gRPC GetJwks failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getAccount(
            GetAccountRequest request, StreamObserver<GetAccountResponse> responseObserver) {
        log.debug("gRPC GetAccount request received for account: {}", request.getAccountId());

        try {
            UUID accountId = mapper.extractAccountId(request);
            AccountResponse account = accountManagementService.getAccountById(accountId);
            GetAccountResponse grpcResponse = mapper.toGetAccountResponseProto(account);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC GetAccount completed successfully for account: {}", accountId);
        } catch (Exception e) {
            log.error("gRPC GetAccount failed for account: {}", request.getAccountId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getSessions(
            GetSessionsRequest request, StreamObserver<GetSessionsResponse> responseObserver) {
        log.debug("gRPC GetSessions request received for account: {}", request.getAccountId());

        try {
            UUID accountId = mapper.extractAccountIdFromGetSessions(request);
            List<SessionResponse> sessions = sessionManagementService.getActiveSessions(accountId);
            GetSessionsResponse grpcResponse = mapper.toGetSessionsResponseProto(sessions);

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC GetSessions completed successfully for account: {}", accountId);
        } catch (Exception e) {
            log.error("gRPC GetSessions failed for account: {}", request.getAccountId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void revokeSession(
            RevokeSessionRequest request, StreamObserver<RevokeSessionResponse> responseObserver) {
        log.debug("gRPC RevokeSession request received for session: {}", request.getSessionId());

        try {
            UUID accountId = mapper.extractAccountIdFromRevokeSession(request);
            UUID sessionId = mapper.extractSessionId(request);
            sessionManagementService.revokeSession(sessionId, accountId);

            RevokeSessionResponse grpcResponse =
                    mapper.toRevokeSessionResponseProto(true, "Session revoked successfully");

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();

            log.info("gRPC RevokeSession completed successfully for session: {}", sessionId);
        } catch (Exception e) {
            log.error("gRPC RevokeSession failed for session: {}", request.getSessionId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void healthCheck(
            HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        log.debug("gRPC HealthCheck request received");

        HealthCheckResponse response =
                mapper.toHealthCheckResponseProto(
                        HealthCheckResponse.ServingStatus.SERVING,
                        "Auth service is healthy",
                        Instant.now().toEpochMilli());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
