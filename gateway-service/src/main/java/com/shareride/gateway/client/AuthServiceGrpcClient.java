package com.shareride.gateway.client;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.grpc.auth.v1.AuthServiceGrpc;
import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.GetSessionsRequest;
import com.shareride.common.grpc.auth.v1.GetSessionsResponse;
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
import com.shareride.common.grpc.auth.v1.VerifyEmailRequest;
import com.shareride.common.grpc.auth.v1.VerifyEmailResponse;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive gRPC client for Auth Service wrapping AuthServiceBlockingStub. Schedules blocking gRPC
 * calls on Schedulers.boundedElastic() to prevent blocking the event loop and maps gRPC
 * StatusRuntimeException to domain exceptions via GrpcStatusExceptionMapper.
 *
 * <p>All operations return Mono for reactive composition in Gateway's WebFlux handlers.
 */
@Slf4j
@Component
public class AuthServiceGrpcClient {

    private final AuthServiceGrpc.AuthServiceBlockingStub blockingStub;

    public AuthServiceGrpcClient(GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.getChannel("auth-service");
        this.blockingStub = AuthServiceGrpc.newBlockingStub(channel);
        log.info("AuthServiceGrpcClient initialized with channel to auth-service");
    }

    /**
     * Register a new user account.
     *
     * @param request the registration request
     * @return Mono emitting RegisterResponse or error
     */
    public Mono<RegisterResponse> register(RegisterRequest request) {
        return Mono.fromCallable(() -> blockingStub.register(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "Register successful: accountId={}",
                                        response.getAccountId()))
                .doOnError(e -> log.error("Register failed: {}", e.getMessage()));
    }

    /**
     * Authenticate user and create session.
     *
     * @param request the login request
     * @return Mono emitting LoginResponse with tokens or error
     */
    public Mono<LoginResponse> login(LoginRequest request) {
        return Mono.fromCallable(() -> blockingStub.login(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "Login successful: sessionId={}, accountId={}",
                                        response.getSessionId(),
                                        response.getAccount().getAccountId()))
                .doOnError(e -> log.error("Login failed: {}", e.getMessage()));
    }

    /**
     * Refresh access token using refresh token.
     *
     * @param request the refresh token request
     * @return Mono emitting RefreshTokenResponse with new tokens or error
     */
    public Mono<RefreshTokenResponse> refreshToken(RefreshTokenRequest request) {
        return Mono.fromCallable(() -> blockingStub.refreshToken(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(response -> log.debug("RefreshToken successful"))
                .doOnError(e -> log.error("RefreshToken failed: {}", e.getMessage()));
    }

    /**
     * Logout user and invalidate session.
     *
     * @param request the logout request
     * @return Mono emitting LogoutResponse or error
     */
    public Mono<LogoutResponse> logout(LogoutRequest request) {
        return Mono.fromCallable(() -> blockingStub.logout(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(response -> log.debug("Logout successful: {}", response.getMessage()))
                .doOnError(e -> log.error("Logout failed: {}", e.getMessage()));
    }

    /**
     * Verify email address using verification token.
     *
     * @param request the email verification request
     * @return Mono emitting VerifyEmailResponse or error
     */
    public Mono<VerifyEmailResponse> verifyEmail(VerifyEmailRequest request) {
        return Mono.fromCallable(() -> blockingStub.verifyEmail(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "VerifyEmail successful: accountId={}",
                                        response.getAccountId()))
                .doOnError(e -> log.error("VerifyEmail failed: {}", e.getMessage()));
    }

    /**
     * Resend email verification token.
     *
     * @param request the resend verification request
     * @return Mono emitting ResendVerificationResponse or error
     */
    public Mono<ResendVerificationResponse> resendVerification(ResendVerificationRequest request) {
        return Mono.fromCallable(() -> blockingStub.resendVerification(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "ResendVerification successful: {}", response.getMessage()))
                .doOnError(e -> log.error("ResendVerification failed: {}", e.getMessage()));
    }

    /**
     * Get JWKS public keys for JWT verification.
     *
     * @param request the JWKS request (empty)
     * @return Mono emitting GetJwksResponse with public keys or error
     */
    public Mono<GetJwksResponse> getJwks(GetJwksRequest request) {
        return Mono.fromCallable(() -> blockingStub.getJwks(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug("GetJwks successful: {} keys", response.getKeysCount()))
                .doOnError(e -> log.error("GetJwks failed: {}", e.getMessage()));
    }

    /**
     * Get account details by account ID.
     *
     * @param request the get account request
     * @return Mono emitting GetAccountResponse or error
     */
    public Mono<GetAccountResponse> getAccount(GetAccountRequest request) {
        return Mono.fromCallable(() -> blockingStub.getAccount(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "GetAccount successful: accountId={}",
                                        response.getAccount().getAccountId()))
                .doOnError(e -> log.error("GetAccount failed: {}", e.getMessage()));
    }

    /**
     * Get all active sessions for an account.
     *
     * @param request the get sessions request
     * @return Mono emitting GetSessionsResponse with sessions list or error
     */
    public Mono<GetSessionsResponse> getSessions(GetSessionsRequest request) {
        return Mono.fromCallable(() -> blockingStub.getSessions(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "GetSessions successful: {} sessions",
                                        response.getSessionsCount()))
                .doOnError(e -> log.error("GetSessions failed: {}", e.getMessage()));
    }

    /**
     * Revoke a specific session.
     *
     * @param request the revoke session request
     * @return Mono emitting RevokeSessionResponse or error
     */
    public Mono<RevokeSessionResponse> revokeSession(RevokeSessionRequest request) {
        return Mono.fromCallable(() -> blockingStub.revokeSession(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug("RevokeSession successful: {}", response.getMessage()))
                .doOnError(e -> log.error("RevokeSession failed: {}", e.getMessage()));
    }
}
