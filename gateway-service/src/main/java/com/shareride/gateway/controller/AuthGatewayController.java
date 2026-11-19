package com.shareride.gateway.controller;

import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
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
import com.shareride.common.response.BaseResponse;
import com.shareride.gateway.client.AuthServiceGrpcClient;
import com.shareride.gateway.dto.LoginRequestDto;
import com.shareride.gateway.dto.LogoutRequestDto;
import com.shareride.gateway.dto.RefreshTokenRequestDto;
import com.shareride.gateway.dto.RegisterRequestDto;
import com.shareride.gateway.dto.ResendVerificationRequestDto;
import com.shareride.gateway.dto.RevokeSessionRequestDto;
import com.shareride.gateway.dto.VerifyEmailRequestDto;
import com.shareride.gateway.service.GatewayJwksCacheService;
import com.shareride.gateway.service.GatewayRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Northbound Gateway REST controller translating HTTP/REST requests to gRPC calls to Auth Service.
 * Implements transparent protocol translation per Phase 3 User Story 1 (T022).
 *
 * <p>All endpoints return Mono<ResponseEntity<BaseResponse<T>>> for reactive composition.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and session management")
public class AuthGatewayController {

    private final AuthServiceGrpcClient authServiceGrpcClient;
    private final GatewayRateLimitService rateLimitService;
    private final GatewayJwksCacheService jwksCacheService;

    @Operation(summary = "Register new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping("/register")
    public Mono<ResponseEntity<BaseResponse<RegisterResponse>>> register(
            @RequestBody RegisterRequestDto dto) {
        return rateLimitService
                .checkRegisterRateLimit(dto.getEmail())
                .then(
                        Mono.defer(
                                () -> {
                                    RegisterRequest grpcRequest =
                                            RegisterRequest.newBuilder()
                                                    .setEmail(dto.getEmail())
                                                    .setPassword(dto.getPassword())
                                                    .setConfirmPassword(dto.getConfirmPassword())
                                                    .build();

                                    return authServiceGrpcClient
                                            .register(grpcRequest)
                                            .map(
                                                    response ->
                                                            ResponseEntity.status(
                                                                            HttpStatus.CREATED)
                                                                    .body(
                                                                            BaseResponse.success(
                                                                                    response)))
                                            .doOnSuccess(
                                                    r ->
                                                            log.info(
                                                                    "Register successful: {}",
                                                                    r.getBody()
                                                                            .getData()
                                                                            .getAccountId()));
                                }));
    }

    @Operation(summary = "Login with email and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "429", description = "Too many requests")
    })
    @PostMapping("/login")
    public Mono<ResponseEntity<BaseResponse<LoginResponse>>> login(
            @RequestBody LoginRequestDto dto) {
        return rateLimitService
                .checkLoginRateLimit(dto.getEmail())
                .then(
                        Mono.defer(
                                () -> {
                                    LoginRequest grpcRequest =
                                            LoginRequest.newBuilder()
                                                    .setEmail(dto.getEmail())
                                                    .setPassword(dto.getPassword())
                                                    .setDeviceId(
                                                            dto.getDeviceId() != null
                                                                    ? dto.getDeviceId()
                                                                    : "")
                                                    .setDeviceName(
                                                            dto.getDeviceName() != null
                                                                    ? dto.getDeviceName()
                                                                    : "")
                                                    .setClientIp(
                                                            "") // Extracted from ServerWebExchange
                                                    // in
                                                    // production
                                                    .setUserAgent("")
                                                    .build();

                                    return authServiceGrpcClient
                                            .login(grpcRequest)
                                            .map(
                                                    response ->
                                                            ResponseEntity.ok(
                                                                    BaseResponse.success(response)))
                                            .doOnSuccess(
                                                    r ->
                                                            log.info(
                                                                    "Login successful:"
                                                                            + " sessionId={}",
                                                                    r.getBody()
                                                                            .getData()
                                                                            .getSessionId()));
                                }));
    }

    @Operation(summary = "Refresh access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed"),
        @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    })
    @PostMapping("/refresh")
    public Mono<ResponseEntity<BaseResponse<RefreshTokenResponse>>> refreshToken(
            @RequestBody RefreshTokenRequestDto dto) {
        return rateLimitService
                .checkRefreshRateLimit(dto.getRefreshToken())
                .then(
                        Mono.defer(
                                () -> {
                                    RefreshTokenRequest grpcRequest =
                                            RefreshTokenRequest.newBuilder()
                                                    .setRefreshToken(dto.getRefreshToken())
                                                    .setClientIp(
                                                            "") // Extracted from ServerWebExchange
                                                    // in
                                                    // production
                                                    .setUserAgent("")
                                                    .build();

                                    return authServiceGrpcClient
                                            .refreshToken(grpcRequest)
                                            .map(
                                                    response ->
                                                            ResponseEntity.ok(
                                                                    BaseResponse.success(response)))
                                            .doOnSuccess(r -> log.info("RefreshToken successful"));
                                }));
    }

    @Operation(summary = "Logout and invalidate session")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Logout successful")})
    @PostMapping("/logout")
    public Mono<ResponseEntity<BaseResponse<LogoutResponse>>> logout(
            @RequestBody LogoutRequestDto dto) {
        LogoutRequest grpcRequest =
                LogoutRequest.newBuilder()
                        .setAccessToken(dto.getAccessToken() != null ? dto.getAccessToken() : "")
                        .setRefreshToken(dto.getRefreshToken() != null ? dto.getRefreshToken() : "")
                        .setSessionId(dto.getSessionId() != null ? dto.getSessionId() : "")
                        .build();

        return authServiceGrpcClient
                .logout(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(r -> log.info("Logout successful"));
    }

    @Operation(summary = "Verify email address")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified"),
        @ApiResponse(responseCode = "400", description = "Invalid token")
    })
    @PostMapping("/verify-email")
    public Mono<ResponseEntity<BaseResponse<VerifyEmailResponse>>> verifyEmail(
            @RequestBody VerifyEmailRequestDto dto) {
        VerifyEmailRequest grpcRequest =
                VerifyEmailRequest.newBuilder().setToken(dto.getToken()).build();

        return authServiceGrpcClient
                .verifyEmail(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(
                        r ->
                                log.info(
                                        "VerifyEmail successful: {}",
                                        r.getBody().getData().getAccountId()));
    }

    @Operation(summary = "Resend email verification")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Verification email sent")})
    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<BaseResponse<ResendVerificationResponse>>> resendVerification(
            @RequestBody ResendVerificationRequestDto dto) {
        ResendVerificationRequest grpcRequest =
                ResendVerificationRequest.newBuilder().setEmail(dto.getEmail()).build();

        return authServiceGrpcClient
                .resendVerification(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(r -> log.info("ResendVerification successful"));
    }

    @Operation(summary = "Get JWKS public keys")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "JWKS retrieved")})
    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetJwksResponse> getJwksAlt() {
        return jwksCacheService.getJwksWithCache();
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetJwksResponse> getJwks() {
        return jwksCacheService.getJwksWithCache();
    }

    @Operation(summary = "Get account details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account retrieved"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/accounts/{accountId}")
    public Mono<ResponseEntity<BaseResponse<GetAccountResponse>>> getAccount(
            @PathVariable String accountId) {
        GetAccountRequest grpcRequest =
                GetAccountRequest.newBuilder().setAccountId(accountId).build();

        return authServiceGrpcClient
                .getAccount(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(r -> log.info("GetAccount successful: {}", accountId));
    }

    @Operation(summary = "Get active sessions")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Sessions retrieved")})
    @GetMapping("/sessions")
    public Mono<ResponseEntity<BaseResponse<GetSessionsResponse>>> getSessions() {
        // Extract accountId from JWT via SecurityContext in production
        String accountId = ""; // Placeholder
        GetSessionsRequest grpcRequest =
                GetSessionsRequest.newBuilder().setAccountId(accountId).build();

        return authServiceGrpcClient
                .getSessions(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(r -> log.info("GetSessions successful"));
    }

    @Operation(summary = "Revoke session")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Session revoked")})
    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<BaseResponse<RevokeSessionResponse>>> revokeSession(
            @PathVariable String sessionId, @RequestBody RevokeSessionRequestDto dto) {
        RevokeSessionRequest grpcRequest =
                RevokeSessionRequest.newBuilder()
                        .setAccountId(dto.getAccountId())
                        .setSessionId(sessionId)
                        .build();

        return authServiceGrpcClient
                .revokeSession(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(r -> log.info("RevokeSession successful: {}", sessionId));
    }
}
