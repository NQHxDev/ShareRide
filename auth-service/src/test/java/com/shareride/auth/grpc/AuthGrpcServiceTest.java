package com.shareride.auth.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.dto.AccountResponseDTO;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.grpc.mapper.AuthProtoMapper;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.AccountVerificationService;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
import com.shareride.common.grpc.auth.v1.GetJwksResponse;
import com.shareride.common.grpc.auth.v1.GetSessionsRequest;
import com.shareride.common.grpc.auth.v1.GetSessionsResponse;
import com.shareride.common.grpc.auth.v1.HealthCheckRequest;
import com.shareride.common.grpc.auth.v1.HealthCheckResponse;
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
import com.shareride.common.util.UuidUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AuthGrpcService validating all 11 RPC handlers with mock domain services. Per T027
 * (Phase 4 User Story 2).
 */
@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceTest {

    @Mock private AuthService authService;
    @Mock private AccountManagementService accountManagementService;
    @Mock private SessionManagementService sessionManagementService;
    @Mock private AccountVerificationService accountVerificationService;

    @Mock private StreamObserver<RegisterResponse> registerResponseObserver;
    @Mock private StreamObserver<LoginResponse> loginResponseObserver;
    @Mock private StreamObserver<RefreshTokenResponse> refreshTokenResponseObserver;
    @Mock private StreamObserver<LogoutResponse> logoutResponseObserver;
    @Mock private StreamObserver<VerifyEmailResponse> verifyEmailResponseObserver;
    @Mock private StreamObserver<ResendVerificationResponse> resendVerificationResponseObserver;
    @Mock private StreamObserver<GetJwksResponse> getJwksResponseObserver;
    @Mock private StreamObserver<GetAccountResponse> getAccountResponseObserver;
    @Mock private StreamObserver<GetSessionsResponse> getSessionsResponseObserver;
    @Mock private StreamObserver<RevokeSessionResponse> revokeSessionResponseObserver;
    @Mock private StreamObserver<HealthCheckResponse> healthCheckResponseObserver;

    @Captor private ArgumentCaptor<RegisterResponse> registerResponseCaptor;
    @Captor private ArgumentCaptor<LoginResponse> loginResponseCaptor;
    @Captor private ArgumentCaptor<GetAccountResponse> getAccountResponseCaptor;
    @Captor private ArgumentCaptor<HealthCheckResponse> healthCheckResponseCaptor;

    private AuthGrpcService authGrpcService;
    private AuthProtoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AuthProtoMapper();
        authGrpcService =
                new AuthGrpcService(
                        authService,
                        accountManagementService,
                        sessionManagementService,
                        accountVerificationService,
                        mapper);
    }

    @Test
    void register_shouldReturnRegisterResponse_whenValidRequest() {
        RegisterRequest request =
                RegisterRequest.newBuilder()
                        .setEmail("test@example.com")
                        .setPassword("SecurePass123")
                        .setConfirmPassword("SecurePass123")
                        .build();

        String accountId = UuidUtils.v7AsString();
        AccountResponseDTO mockResponse =
                AccountResponseDTO.builder()
                        .accountId(UUID.fromString(accountId))
                        .email("test@example.com")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .roles(Set.of("ROLE_RIDER"))
                        .createdAt(Instant.now())
                        .build();

        when(authService.register(any())).thenReturn(mockResponse);

        authGrpcService.register(request, registerResponseObserver);

        verify(registerResponseObserver).onNext(registerResponseCaptor.capture());
        verify(registerResponseObserver).onCompleted();

        RegisterResponse response = registerResponseCaptor.getValue();
        assertThat(response.getAccountId()).isEqualTo(accountId);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getRoleCode()).isEqualTo("ROLE_RIDER");
        assertThat(response.getVerificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void login_shouldReturnLoginResponse_whenValidCredentials() {
        LoginRequest request =
                LoginRequest.newBuilder()
                        .setEmail("test@example.com")
                        .setPassword("SecurePass123")
                        .setDeviceId("device-001")
                        .setDeviceName("iPhone 15")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("iOS/17.0")
                        .build();

        AuthResponse mockResponse =
                AuthResponse.builder()
                        .accessToken("mock.access.token")
                        .refreshToken("mock.refresh.token")
                        .tokenType("Bearer")
                        .expiresIn(Instant.now().plusSeconds(3600))
                        .sessionId(UuidUtils.v7())
                        .account(
                                AccountResponseDTO.builder()
                                        .accountId(UuidUtils.v7())
                                        .email("test@example.com")
                                        .status(AccountStatus.ACTIVE)
                                        .build())
                        .build();

        when(authService.login(any(), anyString(), anyString())).thenReturn(mockResponse);

        authGrpcService.login(request, loginResponseObserver);

        verify(loginResponseObserver).onNext(loginResponseCaptor.capture());
        verify(loginResponseObserver).onCompleted();

        LoginResponse response = loginResponseCaptor.getValue();
        assertThat(response.getAccessToken()).isEqualTo("mock.access.token");
        assertThat(response.getRefreshToken()).isEqualTo("mock.refresh.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isGreaterThan(0);
    }

    @Test
    void getAccount_shouldReturnAccountDetails_whenAccountExists() {
        String accountId = UuidUtils.v7AsString();
        GetAccountRequest request = GetAccountRequest.newBuilder().setAccountId(accountId).build();

        AccountResponseDTO mockAccount =
                AccountResponseDTO.builder()
                        .accountId(UUID.fromString(accountId))
                        .email("test@example.com")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        when(accountManagementService.getAccountById(any(UUID.class))).thenReturn(mockAccount);

        authGrpcService.getAccount(request, getAccountResponseObserver);

        verify(getAccountResponseObserver).onNext(getAccountResponseCaptor.capture());
        verify(getAccountResponseObserver).onCompleted();

        GetAccountResponse response = getAccountResponseCaptor.getValue();
        assertThat(response.getAccount().getAccountId()).isEqualTo(accountId);
        assertThat(response.getAccount().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getAccount().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getJwks_shouldReturnPublicKeys_whenCalled() {
        GetJwksRequest request = GetJwksRequest.newBuilder().build();

        Map<String, Object> mockJwks =
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

        when(authService.getJwks()).thenReturn(mockJwks);

        authGrpcService.getJwks(request, getJwksResponseObserver);

        verify(getJwksResponseObserver).onNext(any(GetJwksResponse.class));
        verify(getJwksResponseObserver).onCompleted();
    }

    @Test
    void healthCheck_shouldReturnServing_whenServiceIsHealthy() {
        HealthCheckRequest request =
                HealthCheckRequest.newBuilder().setServiceName("auth-service").build();

        authGrpcService.healthCheck(request, healthCheckResponseObserver);

        verify(healthCheckResponseObserver).onNext(healthCheckResponseCaptor.capture());
        verify(healthCheckResponseObserver).onCompleted();

        HealthCheckResponse response = healthCheckResponseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        assertThat(response.getMessage()).contains("healthy");
    }

    @Test
    void logout_shouldInvalidateSession_whenValidRequest() {
        LogoutRequest request =
                LogoutRequest.newBuilder()
                        .setAccessToken("mock.access.token")
                        .setRefreshToken("mock.refresh.token")
                        .setSessionId(UuidUtils.v7AsString())
                        .build();

        authGrpcService.logout(request, logoutResponseObserver);

        verify(logoutResponseObserver).onNext(any(LogoutResponse.class));
        verify(logoutResponseObserver).onCompleted();
    }

    @Test
    void verifyEmail_shouldVerifyAccount_whenValidToken() {
        VerifyEmailRequest request =
                VerifyEmailRequest.newBuilder().setToken("valid-verification-token").build();

        AccountResponseDTO mockAccount =
                AccountResponseDTO.builder().accountId(UuidUtils.v7()).build();
        when(accountVerificationService.verifyEmail(any())).thenReturn(mockAccount);

        authGrpcService.verifyEmail(request, verifyEmailResponseObserver);

        verify(verifyEmailResponseObserver).onNext(any(VerifyEmailResponse.class));
        verify(verifyEmailResponseObserver).onCompleted();
    }

    @Test
    void getSessions_shouldReturnActiveSessions_whenAccountExists() {
        String accountId = UuidUtils.v7AsString();
        GetSessionsRequest request =
                GetSessionsRequest.newBuilder().setAccountId(accountId).build();

        SessionResponse mockSession =
                SessionResponse.builder()
                        .sessionId(UuidUtils.v7())
                        .deviceId("device-001")
                        .deviceName("iPhone 15")
                        .build();

        when(sessionManagementService.getActiveSessions(any(UUID.class)))
                .thenReturn(List.of(mockSession));

        authGrpcService.getSessions(request, getSessionsResponseObserver);

        verify(getSessionsResponseObserver).onNext(any(GetSessionsResponse.class));
        verify(getSessionsResponseObserver).onCompleted();
    }

    @Test
    void revokeSession_shouldRevokeSession_whenValidRequest() {
        RevokeSessionRequest request =
                RevokeSessionRequest.newBuilder()
                        .setAccountId(UuidUtils.v7AsString())
                        .setSessionId(UuidUtils.v7AsString())
                        .build();

        authGrpcService.revokeSession(request, revokeSessionResponseObserver);

        verify(revokeSessionResponseObserver).onNext(any(RevokeSessionResponse.class));
        verify(revokeSessionResponseObserver).onCompleted();
    }

    @Test
    void register_shouldCallOnError_whenExceptionThrown() {
        RegisterRequest request =
                RegisterRequest.newBuilder().setEmail("error@example.com").build();
        when(authService.register(any())).thenThrow(new RuntimeException("Registration error"));

        authGrpcService.register(request, registerResponseObserver);

        verify(registerResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void login_shouldCallOnError_whenExceptionThrown() {
        LoginRequest request = LoginRequest.newBuilder().setEmail("error@example.com").build();
        when(authService.login(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Login error"));

        authGrpcService.login(request, loginResponseObserver);

        verify(loginResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void refreshToken_shouldCallOnError_whenExceptionThrown() {
        RefreshTokenRequest request =
                RefreshTokenRequest.newBuilder().setRefreshToken("invalid.token").build();
        when(authService.refreshToken(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Token refresh error"));

        authGrpcService.refreshToken(request, refreshTokenResponseObserver);

        verify(refreshTokenResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void logout_shouldCallOnError_whenExceptionThrown() {
        LogoutRequest request =
                LogoutRequest.newBuilder()
                        .setAccessToken("token")
                        .setRefreshToken("refresh")
                        .build();
        doThrow(new RuntimeException("Logout error"))
                .when(authService)
                .logout(anyString(), anyString());

        authGrpcService.logout(request, logoutResponseObserver);

        verify(logoutResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void verifyEmail_shouldReturnErrorResponse_whenExceptionThrown() {
        VerifyEmailRequest request = VerifyEmailRequest.newBuilder().setToken("bad-token").build();
        when(accountVerificationService.verifyEmail(any()))
                .thenThrow(new RuntimeException("Verification error"));

        authGrpcService.verifyEmail(request, verifyEmailResponseObserver);

        verify(verifyEmailResponseObserver).onNext(any(VerifyEmailResponse.class));
        verify(verifyEmailResponseObserver).onCompleted();
    }

    @Test
    void resendVerification_shouldReturnErrorResponse_whenExceptionThrown() {
        ResendVerificationRequest request =
                ResendVerificationRequest.newBuilder().setEmail("error@example.com").build();
        when(accountVerificationService.resendVerification(any()))
                .thenThrow(new RuntimeException("Email delivery failed"));

        authGrpcService.resendVerification(request, resendVerificationResponseObserver);

        verify(resendVerificationResponseObserver).onNext(any(ResendVerificationResponse.class));
        verify(resendVerificationResponseObserver).onCompleted();
    }

    @Test
    void getJwks_shouldCallOnError_whenExceptionThrown() {
        GetJwksRequest request = GetJwksRequest.newBuilder().build();
        when(authService.getJwks()).thenThrow(new RuntimeException("JWKS error"));

        authGrpcService.getJwks(request, getJwksResponseObserver);

        verify(getJwksResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void getAccount_shouldCallOnError_whenExceptionThrown() {
        GetAccountRequest request =
                GetAccountRequest.newBuilder().setAccountId(UuidUtils.v7AsString()).build();
        when(accountManagementService.getAccountById(any(UUID.class)))
                .thenThrow(new RuntimeException("Account not found"));

        authGrpcService.getAccount(request, getAccountResponseObserver);

        verify(getAccountResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void getSessions_shouldCallOnError_whenExceptionThrown() {
        GetSessionsRequest request =
                GetSessionsRequest.newBuilder().setAccountId(UuidUtils.v7AsString()).build();
        when(sessionManagementService.getActiveSessions(any(UUID.class)))
                .thenThrow(new RuntimeException("Session error"));

        authGrpcService.getSessions(request, getSessionsResponseObserver);

        verify(getSessionsResponseObserver).onError(any(RuntimeException.class));
    }

    @Test
    void revokeSession_shouldCallOnError_whenExceptionThrown() {
        RevokeSessionRequest request =
                RevokeSessionRequest.newBuilder()
                        .setAccountId(UuidUtils.v7AsString())
                        .setSessionId(UuidUtils.v7AsString())
                        .build();
        doThrow(new RuntimeException("Revoke error"))
                .when(sessionManagementService)
                .revokeSession(any(UUID.class), any(UUID.class));

        authGrpcService.revokeSession(request, revokeSessionResponseObserver);

        verify(revokeSessionResponseObserver).onError(any(RuntimeException.class));
    }
}
