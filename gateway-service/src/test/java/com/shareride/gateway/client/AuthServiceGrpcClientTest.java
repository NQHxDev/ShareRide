package com.shareride.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.grpc.auth.v1.AccountProto;
import com.shareride.common.grpc.auth.v1.AuthServiceGrpc;
import com.shareride.common.grpc.auth.v1.GetAccountRequest;
import com.shareride.common.grpc.auth.v1.GetAccountResponse;
import com.shareride.common.grpc.auth.v1.GetJwksRequest;
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
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

/**
 * Unit tests for AuthServiceGrpcClient verifying gRPC call delegation, exception mapping, and
 * reactive scheduling on boundedElastic(). Uses in-process gRPC for fast, isolated testing.
 */
@DisplayName("AuthServiceGrpcClient Unit Tests")
class AuthServiceGrpcClientTest {

    @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private AuthServiceGrpcClient client;
    private TestAuthServiceImpl testService;
    private io.grpc.Server server;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        testService = new TestAuthServiceImpl();

        server =
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(testService)
                        .build()
                        .start();

        ManagedChannel channel =
                InProcessChannelBuilder.forName(serverName).directExecutor().build();

        GrpcChannelFactory channelFactory = Mockito.mock(GrpcChannelFactory.class);
        Mockito.when(channelFactory.getChannel("auth-service")).thenReturn(channel);

        client = new AuthServiceGrpcClient(channelFactory);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("register() should return RegisterResponse")
    void testRegister_shouldReturnRegisterResponse() {
        RegisterRequest request =
                RegisterRequest.newBuilder()
                        .setEmail("test@example.com")
                        .setPassword("password123")
                        .setConfirmPassword("password123")
                        .build();

        StepVerifier.create(client.register(request))
                .assertNext(
                        response -> {
                            assertThat(response.getAccountId()).isEqualTo("account-123");
                            assertThat(response.getEmail()).isEqualTo("test@example.com");
                            assertThat(response.getVerificationStatus()).isEqualTo("PENDING");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("login() should return LoginResponse with tokens")
    void testLogin_shouldReturnLoginResponse() {
        LoginRequest request =
                LoginRequest.newBuilder()
                        .setEmail("test@example.com")
                        .setPassword("password123")
                        .setDeviceId("device-123")
                        .setDeviceName("iPhone 14")
                        .setClientIp("192.168.1.1")
                        .setUserAgent("ShareRide iOS App/1.0")
                        .build();

        StepVerifier.create(client.login(request))
                .assertNext(
                        response -> {
                            assertThat(response.getAccessToken()).isEqualTo("access-token-xyz");
                            assertThat(response.getRefreshToken()).isEqualTo("refresh-token-xyz");
                            assertThat(response.getSessionId()).isEqualTo("session-123");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("refreshToken() should return new tokens")
    void testRefreshToken_shouldReturnRefreshTokenResponse() {
        RefreshTokenRequest request =
                RefreshTokenRequest.newBuilder()
                        .setRefreshToken("refresh-token-xyz")
                        .setClientIp("192.168.1.1")
                        .setUserAgent("ShareRide iOS App/1.0")
                        .build();

        StepVerifier.create(client.refreshToken(request))
                .assertNext(
                        response -> {
                            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
                            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("logout() should return success message")
    void testLogout_shouldReturnLogoutResponse() {
        LogoutRequest request =
                LogoutRequest.newBuilder()
                        .setAccessToken("access-token-abc")
                        .setRefreshToken("refresh-token-xyz")
                        .setSessionId("session-123")
                        .build();

        StepVerifier.create(client.logout(request))
                .assertNext(
                        response -> {
                            assertThat(response.getSuccess()).isTrue();
                            assertThat(response.getMessage()).isEqualTo("Logout successful");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyEmail() should return success")
    void testVerifyEmail_shouldReturnVerifyEmailResponse() {
        VerifyEmailRequest request =
                VerifyEmailRequest.newBuilder().setToken("verify-token-123").build();

        StepVerifier.create(client.verifyEmail(request))
                .assertNext(
                        response -> {
                            assertThat(response.getSuccess()).isTrue();
                            assertThat(response.getAccountId()).isEqualTo("account-123");
                            assertThat(response.getMessage())
                                    .isEqualTo("Email verified successfully");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("resendVerification() should return success")
    void testResendVerification_shouldReturnResendVerificationResponse() {
        ResendVerificationRequest request =
                ResendVerificationRequest.newBuilder().setEmail("test@example.com").build();

        StepVerifier.create(client.resendVerification(request))
                .assertNext(
                        response -> {
                            assertThat(response.getSuccess()).isTrue();
                            assertThat(response.getMessage())
                                    .isEqualTo("Verification email resent");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("getJwks() should return public keys")
    void testGetJwks_shouldReturnJwksResponse() {
        GetJwksRequest request = GetJwksRequest.newBuilder().build();

        StepVerifier.create(client.getJwks(request))
                .assertNext(
                        response -> {
                            assertThat(response.getKeysCount()).isEqualTo(1);
                            assertThat(response.getKeys(0).getKid()).isEqualTo("shareride-key-1");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("getAccount() should return account details")
    void testGetAccount_shouldReturnAccountResponse() {
        GetAccountRequest request =
                GetAccountRequest.newBuilder().setAccountId("account-123").build();

        StepVerifier.create(client.getAccount(request))
                .assertNext(
                        response -> {
                            assertThat(response.getAccount().getAccountId())
                                    .isEqualTo("account-123");
                            assertThat(response.getAccount().getEmail())
                                    .isEqualTo("test@example.com");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("getSessions() should return active sessions")
    void testGetSessions_shouldReturnSessionsResponse() {
        GetSessionsRequest request =
                GetSessionsRequest.newBuilder().setAccountId("account-123").build();

        StepVerifier.create(client.getSessions(request))
                .assertNext(
                        response -> {
                            assertThat(response.getSessionsCount()).isEqualTo(1);
                            assertThat(response.getSessions(0).getSessionId())
                                    .isEqualTo("session-123");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("revokeSession() should revoke session")
    void testRevokeSession_shouldReturnRevokeSessionResponse() {
        RevokeSessionRequest request =
                RevokeSessionRequest.newBuilder()
                        .setAccountId("account-123")
                        .setSessionId("session-123")
                        .build();

        StepVerifier.create(client.revokeSession(request))
                .assertNext(
                        response -> {
                            assertThat(response.getMessage()).isEqualTo("Session revoked");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("login() with invalid credentials should throw UnauthorizedException")
    void testLogin_whenInvalidCredentials_shouldThrowUnauthorizedException() {
        testService.setNextError(Status.UNAUTHENTICATED.asRuntimeException());

        LoginRequest request =
                LoginRequest.newBuilder()
                        .setEmail("wrong@example.com")
                        .setPassword("wrongpassword")
                        .build();

        StepVerifier.create(client.login(request))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    @Test
    @DisplayName("getAccount() with non-existent ID should throw ResourceNotFoundException")
    void testGetAccount_whenNotFound_shouldThrowResourceNotFoundException() {
        testService.setNextError(Status.NOT_FOUND.asRuntimeException());

        GetAccountRequest request =
                GetAccountRequest.newBuilder().setAccountId("nonexistent-id").build();

        StepVerifier.create(client.getAccount(request))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("register() with invalid input should throw BadRequestException")
    void testRegister_whenInvalidInput_shouldThrowBadRequestException() {
        testService.setNextError(Status.INVALID_ARGUMENT.asRuntimeException());

        RegisterRequest request =
                RegisterRequest.newBuilder().setEmail("invalid-email").setPassword("short").build();

        StepVerifier.create(client.register(request))
                .expectError(BadRequestException.class)
                .verify();
    }

    /** Test implementation of AuthService for in-process gRPC testing. */
    private static class TestAuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

        private StatusRuntimeException nextError;

        void setNextError(StatusRuntimeException error) {
            this.nextError = error;
        }

        @Override
        public void register(RegisterRequest request, StreamObserver<RegisterResponse> observer) {
            if (nextError != null) {
                observer.onError(nextError);
                nextError = null;
                return;
            }
            observer.onNext(
                    RegisterResponse.newBuilder()
                            .setAccountId("account-123")
                            .setEmail("test@example.com")
                            .setRoleCode("RIDER")
                            .setVerificationStatus("PENDING")
                            .build());
            observer.onCompleted();
        }

        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> observer) {
            if (nextError != null) {
                observer.onError(nextError);
                nextError = null;
                return;
            }
            observer.onNext(
                    LoginResponse.newBuilder()
                            .setAccessToken("access-token-xyz")
                            .setRefreshToken("refresh-token-xyz")
                            .setSessionId("session-123")
                            .setAccount(
                                    AccountProto.newBuilder()
                                            .setAccountId("account-123")
                                            .setEmail("test@example.com")
                                            .build())
                            .build());
            observer.onCompleted();
        }

        @Override
        public void refreshToken(
                RefreshTokenRequest request, StreamObserver<RefreshTokenResponse> observer) {
            if (nextError != null) {
                observer.onError(nextError);
                nextError = null;
                return;
            }
            observer.onNext(
                    RefreshTokenResponse.newBuilder()
                            .setAccessToken("new-access-token")
                            .setRefreshToken("new-refresh-token")
                            .build());
            observer.onCompleted();
        }

        @Override
        public void logout(LogoutRequest request, StreamObserver<LogoutResponse> observer) {
            observer.onNext(
                    LogoutResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("Logout successful")
                            .build());
            observer.onCompleted();
        }

        @Override
        public void verifyEmail(
                VerifyEmailRequest request, StreamObserver<VerifyEmailResponse> observer) {
            observer.onNext(
                    VerifyEmailResponse.newBuilder()
                            .setSuccess(true)
                            .setAccountId("account-123")
                            .setMessage("Email verified successfully")
                            .build());
            observer.onCompleted();
        }

        @Override
        public void resendVerification(
                ResendVerificationRequest request,
                StreamObserver<ResendVerificationResponse> observer) {
            observer.onNext(
                    ResendVerificationResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("Verification email resent")
                            .build());
            observer.onCompleted();
        }

        @Override
        public void getJwks(GetJwksRequest request, StreamObserver<GetJwksResponse> observer) {
            observer.onNext(
                    GetJwksResponse.newBuilder()
                            .addKeys(
                                    JwkKeyProto.newBuilder()
                                            .setKid("shareride-key-1")
                                            .setKty("OKP")
                                            .setCrv("Ed25519")
                                            .setX("mock-x-value")
                                            .setAlg("EdDSA")
                                            .setUse("sig")
                                            .build())
                            .build());
            observer.onCompleted();
        }

        @Override
        public void getAccount(
                GetAccountRequest request, StreamObserver<GetAccountResponse> observer) {
            if (nextError != null) {
                observer.onError(nextError);
                nextError = null;
                return;
            }
            observer.onNext(
                    GetAccountResponse.newBuilder()
                            .setAccount(
                                    AccountProto.newBuilder()
                                            .setAccountId("account-123")
                                            .setEmail("test@example.com")
                                            .build())
                            .build());
            observer.onCompleted();
        }

        @Override
        public void getSessions(
                GetSessionsRequest request, StreamObserver<GetSessionsResponse> observer) {
            observer.onNext(
                    GetSessionsResponse.newBuilder()
                            .addSessions(
                                    SessionProto.newBuilder()
                                            .setSessionId("session-123")
                                            .setDeviceName("Test Device")
                                            .build())
                            .build());
            observer.onCompleted();
        }

        @Override
        public void revokeSession(
                RevokeSessionRequest request, StreamObserver<RevokeSessionResponse> observer) {
            observer.onNext(
                    RevokeSessionResponse.newBuilder().setMessage("Session revoked").build());
            observer.onCompleted();
        }
    }
}
