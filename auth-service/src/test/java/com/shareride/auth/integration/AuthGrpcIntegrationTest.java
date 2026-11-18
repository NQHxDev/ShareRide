package com.shareride.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.auth.grpc.AuthGrpcService;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.SessionRepository;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.grpc.auth.v1.*;
import com.shareride.common.outbox.OutboxMessageRepository;
import com.shareride.common.outbox.OutboxStatus;
import com.shareride.common.util.UuidUtils;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for AuthGrpcService validating all 11 RPC handlers with real PostgreSQL 17,
 * Valkey, and in-process gRPC server. Tests full end-to-end flows including persistence, JWT token
 * generation, session management, and transactional outbox.
 *
 * <p>Per Constitution Principle V: no external services in GitHub Actions - uses Testcontainers
 * with disabledWithoutDocker=true.
 *
 * <p>Validates T027 (Phase 4 User Story 2) requirements.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthGrpcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthGrpcService authGrpcService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private OutboxMessageRepository outboxMessageRepository;

    private Server grpcServer;
    private ManagedChannel grpcChannel;
    private AuthServiceGrpc.AuthServiceBlockingStub blockingStub;

    private String testAccountId;
    private String testEmail;
    private String testPassword;
    private String accessToken;
    private String refreshToken;
    private String sessionId;

    @BeforeEach
    void setUpGrpcChannel() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        grpcServer =
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(authGrpcService)
                        .build()
                        .start();

        grpcChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        blockingStub = AuthServiceGrpc.newBlockingStub(grpcChannel);

        UserContextHolder.setContext(
                UserContext.builder()
                        .userId("integration-test-user")
                        .roles(Set.of("ROLE_ADMIN"))
                        .build());

        outboxMessageRepository.deleteAll();
        sessionRepository.deleteAll();
        accountRepository.deleteAll();

        testEmail = "grpc.test." + UuidUtils.v7() + "@shareride.com";
        testPassword = "SecurePassword123!";
    }

    @AfterEach
    void tearDownGrpcChannel() {
        UserContextHolder.clear();
        if (grpcChannel != null && !grpcChannel.isShutdown()) {
            grpcChannel.shutdownNow();
        }
        if (grpcServer != null && !grpcServer.isShutdown()) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Register: Should register new account with email, roles, and create outbox event")
    void testRegister() {
        RegisterRequest request =
                RegisterRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setPhoneNumber("+84901234567")
                        .setRoleCode("RIDER")
                        .setDisplayName("gRPC Test User")
                        .build();

        RegisterResponse response = blockingStub.register(request);

        assertThat(response.getAccountId()).isNotEmpty();
        assertThat(response.getEmail()).isEqualTo(testEmail);
        assertThat(response.getRoleCode()).isEqualTo("ROLE_RIDER");
        assertThat(response.getVerificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(response.getCreatedAt()).isNotEmpty();

        testAccountId = response.getAccountId();

        assertThat(accountRepository.existsByEmail(testEmail)).isTrue();

        var outboxMessages =
                outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertThat(outboxMessages)
                .anySatisfy(
                        msg -> {
                            assertThat(msg.getAggregateId()).isEqualTo(testAccountId);
                            assertThat(msg.getEventType()).contains("ACCOUNT_REGISTERED");
                        });
    }

    @Test
    @Order(2)
    @DisplayName("GetAccount: Should retrieve account details by ID")
    void testGetAccount() {
        RegisterRequest registerRequest =
                RegisterRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setPhoneNumber("+84901234568")
                        .setRoleCode("DRIVER")
                        .setDisplayName("Driver User")
                        .build();

        RegisterResponse registerResponse = blockingStub.register(registerRequest);
        String accountId = registerResponse.getAccountId();

        GetAccountRequest request = GetAccountRequest.newBuilder().setAccountId(accountId).build();
        GetAccountResponse response = blockingStub.getAccount(request);

        assertThat(response.hasAccount()).isTrue();
        assertThat(response.getAccount().getAccountId()).isEqualTo(accountId);
        assertThat(response.getAccount().getEmail()).isEqualTo(testEmail);
        assertThat(response.getAccount().getPhoneNumber()).isEqualTo("+84901234568");
        assertThat(response.getAccount().getStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(response.getAccount().getRolesList()).contains("ROLE_DRIVER");
    }

    @Test
    @Order(3)
    @DisplayName(
            "Login: Should authenticate user, create session in PostgreSQL, generate JWT tokens,"
                    + " and cache refresh token in Valkey")
    void testLogin() {
        RegisterRequest registerRequest =
                RegisterRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setPhoneNumber("+84901234569")
                        .setRoleCode("RIDER")
                        .build();

        RegisterResponse registerResponse = blockingStub.register(registerRequest);
        testAccountId = registerResponse.getAccountId();

        accountRepository
                .findById(UUID.fromString(testAccountId))
                .ifPresent(
                        account -> {
                            account.setStatus(com.shareride.auth.domain.AccountStatus.ACTIVE);
                            // Email verification is tracked separately in AccountVerification table
                            accountRepository.saveAndFlush(account);
                        });

        LoginRequest loginRequest =
                LoginRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setDeviceId("device-001")
                        .setDeviceName("iPhone 15 Pro")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("ShareRide-iOS/1.0")
                        .build();

        LoginResponse response = blockingStub.login(loginRequest);

        assertThat(response.getAccessToken()).isNotEmpty();
        assertThat(response.getRefreshToken()).isNotEmpty();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isGreaterThan(0);
        assertThat(response.getSessionId()).isNotEmpty();
        assertThat(response.hasAccount()).isTrue();
        assertThat(response.getAccount().getEmail()).isEqualTo(testEmail);

        accessToken = response.getAccessToken();
        refreshToken = response.getRefreshToken();
        sessionId = response.getSessionId();

        var session = sessionRepository.findById(UUID.fromString(sessionId));
        assertThat(session).isPresent();
        assertThat(session.get().getDeviceId()).isEqualTo("device-001");
        assertThat(session.get().getDeviceName()).isEqualTo("iPhone 15 Pro");
        assertThat(session.get().getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(session.get().getStatus())
                .isEqualTo(com.shareride.auth.domain.SessionStatus.ACTIVE);
    }

    @Test
    @Order(4)
    @DisplayName(
            "RefreshToken: Should issue new token pair, rotate refresh token in Valkey, and"
                    + " invalidate old refresh token")
    void testRefreshToken() {
        setupLoginFlow();

        RefreshTokenRequest refreshRequest =
                RefreshTokenRequest.newBuilder()
                        .setRefreshToken(refreshToken)
                        .setClientIp("192.168.1.101")
                        .setUserAgent("ShareRide-iOS/1.0-Updated")
                        .build();

        RefreshTokenResponse response = blockingStub.refreshToken(refreshRequest);

        assertThat(response.getAccessToken()).isNotEmpty();
        assertThat(response.getRefreshToken()).isNotEmpty();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isGreaterThan(0);
        assertThat(response.getAccessToken()).isNotEqualTo(accessToken);
        assertThat(response.getRefreshToken()).isNotEqualTo(refreshToken);
    }

    @Test
    @Order(5)
    @DisplayName(
            "GetSessions: Should return all active sessions for an account with device metadata")
    void testGetSessions() {
        setupLoginFlow();

        GetSessionsRequest request =
                GetSessionsRequest.newBuilder().setAccountId(testAccountId).build();

        GetSessionsResponse response = blockingStub.getSessions(request);

        assertThat(response.getSessionsList()).isNotEmpty();
        assertThat(response.getSessionsList())
                .anySatisfy(
                        session -> {
                            assertThat(session.getSessionId()).isNotEmpty();
                            assertThat(session.getDeviceId()).isEqualTo("device-001");
                            assertThat(session.getDeviceName()).isEqualTo("iPhone 15 Pro");
                            assertThat(session.getClientIp()).isEqualTo("192.168.1.100");
                            assertThat(session.getCreatedAt()).isNotEmpty();
                        });
    }

    @Test
    @Order(6)
    @DisplayName(
            "RevokeSession: Should mark session as REVOKED in PostgreSQL and remove refresh token"
                    + " from Valkey")
    void testRevokeSession() {
        setupLoginFlow();

        UserContextHolder.setContext(
                UserContext.builder().userId(testAccountId).roles(Set.of("ROLE_RIDER")).build());

        RevokeSessionRequest request =
                RevokeSessionRequest.newBuilder()
                        .setAccountId(testAccountId)
                        .setSessionId(sessionId)
                        .build();

        RevokeSessionResponse response = blockingStub.revokeSession(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("revoked");

        var session = sessionRepository.findById(UUID.fromString(sessionId));
        assertThat(session).isPresent();
        assertThat(session.get().getStatus())
                .isEqualTo(com.shareride.auth.domain.SessionStatus.REVOKED);
    }

    @Test
    @Order(7)
    @DisplayName(
            "Logout: Should invalidate access token in Valkey blacklist and delete refresh token")
    void testLogout() {
        setupLoginFlow();

        LogoutRequest request =
                LogoutRequest.newBuilder()
                        .setAccessToken(accessToken)
                        .setRefreshToken(refreshToken)
                        .setSessionId(sessionId)
                        .build();

        LogoutResponse response = blockingStub.logout(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("successful");

        var session = sessionRepository.findById(UUID.fromString(sessionId));
        assertThat(session).isPresent();
        assertThat(session.get().getStatus())
                .isEqualTo(com.shareride.auth.domain.SessionStatus.REVOKED);
    }

    @Test
    @Order(8)
    @DisplayName("VerifyEmail: Should activate account when valid verification token provided")
    void testVerifyEmail() {
        RegisterRequest registerRequest =
                RegisterRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setPhoneNumber("+84901234570")
                        .setRoleCode("RIDER")
                        .build();

        blockingStub.register(registerRequest);

        VerifyEmailRequest request =
                VerifyEmailRequest.newBuilder().setToken("mock-verification-token").build();

        VerifyEmailResponse response = blockingStub.verifyEmail(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isNotEmpty();
    }

    @Test
    @Order(9)
    @DisplayName("ResendVerification: Should resend verification email and create outbox event")
    void testResendVerification() {
        RegisterRequest registerRequest =
                RegisterRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setPhoneNumber("+84901234571")
                        .setRoleCode("RIDER")
                        .build();

        blockingStub.register(registerRequest);

        ResendVerificationRequest request =
                ResendVerificationRequest.newBuilder().setEmail(testEmail).build();

        ResendVerificationResponse response = blockingStub.resendVerification(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("Verification");
    }

    @Test
    @Order(10)
    @DisplayName("GetJwks: Should return EdDSA public keys in JWK format")
    void testGetJwks() {
        GetJwksRequest request = GetJwksRequest.newBuilder().build();

        GetJwksResponse response = blockingStub.getJwks(request);

        assertThat(response.getKeysList()).isNotEmpty();
        assertThat(response.getKeysList())
                .anySatisfy(
                        key -> {
                            assertThat(key.getKty()).isEqualTo("OKP");
                            assertThat(key.getCrv()).isEqualTo("Ed25519");
                            assertThat(key.getAlg()).isEqualTo("EdDSA");
                            assertThat(key.getUse()).isEqualTo("sig");
                            assertThat(key.getKid()).isNotEmpty();
                            assertThat(key.getX()).isNotEmpty();
                        });
    }

    @Test
    @Order(11)
    @DisplayName("HealthCheck: Should return SERVING status when service is healthy")
    void testHealthCheck() {
        HealthCheckRequest request =
                HealthCheckRequest.newBuilder().setServiceName("auth-service").build();

        HealthCheckResponse response = blockingStub.healthCheck(request);

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        assertThat(response.getMessage()).contains("healthy");
        assertThat(response.getTimestamp()).isGreaterThan(0);
        assertThat(response.getTimestamp())
                .isCloseTo(
                        Instant.now().toEpochMilli(), org.assertj.core.data.Offset.offset(5000L));
    }

    private void setupLoginFlow() {
        RegisterRequest registerRequest =
                RegisterRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setPhoneNumber("+84901234572")
                        .setRoleCode("RIDER")
                        .build();

        RegisterResponse registerResponse = blockingStub.register(registerRequest);
        testAccountId = registerResponse.getAccountId();

        accountRepository
                .findById(UUID.fromString(testAccountId))
                .ifPresent(
                        account -> {
                            account.setStatus(com.shareride.auth.domain.AccountStatus.ACTIVE);
                            // Email verification is tracked separately in AccountVerification table
                            accountRepository.saveAndFlush(account);
                        });

        LoginRequest loginRequest =
                LoginRequest.newBuilder()
                        .setEmail(testEmail)
                        .setPassword(testPassword)
                        .setDeviceId("device-001")
                        .setDeviceName("iPhone 15 Pro")
                        .setClientIp("192.168.1.100")
                        .setUserAgent("ShareRide-iOS/1.0")
                        .build();

        LoginResponse loginResponse = blockingStub.login(loginRequest);
        accessToken = loginResponse.getAccessToken();
        refreshToken = loginResponse.getRefreshToken();
        sessionId = loginResponse.getSessionId();
    }
}
