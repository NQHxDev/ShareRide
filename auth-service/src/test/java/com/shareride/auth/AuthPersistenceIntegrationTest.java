package com.shareride.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthOutboxConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.Session;
import com.shareride.auth.domain.SessionStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RefreshTokenData;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.repository.SessionRepository;
import com.shareride.auth.service.AuthService;
import com.shareride.auth.service.RefreshTokenCacheService;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.outbox.OutboxMessage;
import com.shareride.common.outbox.OutboxMessageRepository;
import com.shareride.common.outbox.OutboxStatus;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import com.shareride.common.security.jwt.JwtUserClaims;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AuthPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AccountRepository accountRepository;

    @Autowired private SessionRepository sessionRepository;

    @Autowired private RoleRepository roleRepository;

    @Autowired private OutboxMessageRepository outboxMessageRepository;

    @Autowired private RefreshTokenCacheService refreshTokenCacheService;

    @Autowired private AuthService authService;

    @Autowired private JwtTokenVerifier jwtTokenVerifier;

    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        outboxMessageRepository.deleteAll();
        sessionRepository.deleteAll();
        accountRepository.deleteAll();
        UserContextHolder.setContext(UserContext.builder().userId("system-admin").build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        outboxMessageRepository.deleteAll();
        sessionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName(
            "Relational Persistence: Should automatically generate UUID and audit timestamps &"
                    + " user")
    void testEntityAuditingAndIdGeneration() {
        Role riderRole = roleRepository.findByCode("ROLE_RIDER").orElse(null);
        Set<Role> roles = new HashSet<>();
        if (riderRole != null) {
            roles.add(riderRole);
        }

        Account account =
                Account.builder()
                        .email("driver.alice@shareride.com")
                        .passwordHash("hashed-pw-123")
                        .phoneNumber("+84988111222")
                        .status(AccountStatus.ACTIVE)
                        .roles(roles)
                        .build();

        Account saved = accountRepository.saveAndFlush(account);

        assertNotNull(
                saved.getId(), "UUID primary key should be auto-generated before persistence");
        assertTrue(
                saved.getVersion() >= 0L, "Initial optimistic lock version must be non-negative");
        assertNotNull(saved.getCreatedAt(), "Created date must be populated by JPA Auditing");
        assertNotNull(saved.getUpdatedAt(), "Updated date must be populated by JPA Auditing");
        assertEquals(
                "system-admin",
                saved.getCreatedBy(),
                "Created by should match current UserContext");
        assertFalse(saved.isDeleted(), "Entity should not be marked deleted by default");
    }

    @Test
    @DisplayName("Optimistic Locking: Version must increment on update and prevent lost updates")
    void testOptimisticLocking() {
        Account account =
                Account.builder()
                        .email("rider.bob@shareride.com")
                        .passwordHash("hashed-pw-456")
                        .phoneNumber("+84988333444")
                        .status(AccountStatus.ACTIVE)
                        .build();

        Account saved = accountRepository.saveAndFlush(account);
        assertEquals(0L, saved.getVersion());

        // Update in new transaction
        transactionTemplate.execute(
                status -> {
                    Account toUpdate = accountRepository.findById(saved.getId()).orElseThrow();
                    toUpdate.setPhoneNumber("+84988333555");
                    accountRepository.saveAndFlush(toUpdate);
                    return null;
                });

        Account updated = accountRepository.findById(saved.getId()).orElseThrow();
        assertEquals("+84988333555", updated.getPhoneNumber());
        assertEquals(1L, updated.getVersion(), "Version should have incremented to 1");
    }

    @Test
    @DisplayName("Soft Delete: Soft-deleted entity is preserved in database with audit metadata")
    void testSoftDeleteAudit() {
        Account account =
                Account.builder()
                        .email("rider.charlie@shareride.com")
                        .passwordHash("hashed-pw-789")
                        .phoneNumber("+84988555666")
                        .status(AccountStatus.ACTIVE)
                        .build();

        Account saved = accountRepository.saveAndFlush(account);

        authService.softDeleteAccount(saved.getId(), "compliance-officer");

        Account fetched = accountRepository.findById(saved.getId()).orElseThrow();
        assertTrue(fetched.isDeleted(), "Account should be marked as deleted");
        assertNotNull(fetched.getDeletedAt(), "Deleted timestamp should be recorded");
        assertEquals(
                "compliance-officer", fetched.getDeletedBy(), "Deleted by actor must be captured");

        // Verify filtered query does not return soft deleted record
        Optional<Account> activeAccount =
                accountRepository.findByEmailAndDeletedFalse("rider.charlie@shareride.com");
        assertTrue(
                activeAccount.isEmpty(),
                "Soft deleted account should not be returned by active queries");
    }

    @Test
    @DisplayName(
            "Transactional Outbox: Registering account must save Account and OutboxMessage"
                    + " atomically")
    void testTransactionalOutboxAtomicity() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("new.driver@shareride.com")
                        .password("SecurePass123")
                        .phoneNumber("+84988777888")
                        .fullName("New Driver")
                        .roleCode("ROLE_DRIVER")
                        .build();

        AccountResponse response = authService.register(request);

        assertNotNull(response.getAccountId());
        assertEquals("new.driver@shareride.com", response.getEmail());

        // Verify Account is persisted
        assertTrue(accountRepository.existsByEmail("new.driver@shareride.com"));

        // Verify Transactional Outbox record exists in the SAME local database
        List<OutboxMessage> outboxMessages =
                outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertFalse(outboxMessages.isEmpty(), "Outbox event must be persisted transactionally");

        OutboxMessage message =
                outboxMessages.stream()
                        .filter(
                                m ->
                                        AuthOutboxConstants.AGGREGATE_TYPE_ACCOUNT.equals(
                                                        m.getAggregateType())
                                                && response.getAccountId()
                                                        .toString()
                                                        .equals(m.getAggregateId()))
                        .findFirst()
                        .orElseThrow();

        assertEquals(AuthOutboxConstants.EventType.ACCOUNT_REGISTERED, message.getEventType());
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertTrue(message.getPayload().contains("new.driver@shareride.com"));
    }

    @Test
    @DisplayName(
            "Authentication & Sessions: Login creates session in DB, signs EdDSA JWT, and stores"
                    + " refresh token in Valkey")
    void testLoginWorkflow() {
        RegisterRequest registerRequest =
                RegisterRequest.builder()
                        .email("login.user@shareride.com")
                        .password("CorrectPassword123")
                        .phoneNumber("+84988999000")
                        .roleCode("ROLE_RIDER")
                        .build();
        authService.register(registerRequest);

        // Login success with device metadata
        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("login.user@shareride.com")
                        .password("CorrectPassword123")
                        .deviceId("device-abc-123")
                        .deviceName("iPhone 15 Pro")
                        .deviceType("MOBILE")
                        .location("Hanoi, Vietnam")
                        .build();
        AuthResponse authResponse =
                authService.login(loginRequest, "127.0.0.1", "ShareRide-iOS/1.0");

        assertNotNull(authResponse.getAccessToken());
        assertNotNull(authResponse.getRefreshToken());
        assertNotNull(authResponse.getSessionId());
        assertEquals(AuthConstants.TOKEN_TYPE_BEARER, authResponse.getTokenType());
        assertEquals("login.user@shareride.com", authResponse.getAccount().getEmail());

        // Verify session in DB
        Session session = sessionRepository.findById(authResponse.getSessionId()).orElseThrow();
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertEquals("device-abc-123", session.getDeviceId());
        assertEquals("iPhone 15 Pro", session.getDeviceName());
        assertEquals("127.0.0.1", session.getIpAddress());

        // Cryptographically verify the Access Token using JwtTokenVerifier
        JwtUserClaims claims = jwtTokenVerifier.verify(authResponse.getAccessToken());
        assertNotNull(claims.jti(), "JTI must be present on token");
        assertEquals("login.user@shareride.com", claims.email());
        assertTrue(claims.hasRole("ROLE_RIDER"));
        assertEquals(session.getId(), claims.sessionId());
        assertEquals("device-abc-123", claims.deviceId());

        // Verify refresh token in Valkey
        Optional<RefreshTokenData> tokenData =
                refreshTokenCacheService.getRefreshTokenData(authResponse.getRefreshToken());
        assertTrue(tokenData.isPresent(), "Refresh token must exist in Valkey");
        assertEquals(session.getId(), tokenData.get().getSessionId());

        // Login failure - wrong password
        LoginRequest badLogin =
                LoginRequest.builder()
                        .email("login.user@shareride.com")
                        .password("WrongPassword")
                        .build();
        assertThrows(Exception.class, () -> authService.login(badLogin, null, null));
    }

    @Test
    @DisplayName("Token Rotation: Refresh token succeeds and rotates in Valkey")
    void testTokenRotation() {
        RegisterRequest registerRequest =
                RegisterRequest.builder()
                        .email("refresh.user@shareride.com")
                        .password("CorrectPassword123")
                        .phoneNumber("+84988999111")
                        .build();
        authService.register(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("refresh.user@shareride.com")
                        .password("CorrectPassword123")
                        .build();
        AuthResponse loginResponse = authService.login(loginRequest, "127.0.0.1", "TestAgent");
        String initialRefreshToken = loginResponse.getRefreshToken();

        // Refresh token
        AuthResponse refreshResponse =
                authService.refreshToken(initialRefreshToken, "127.0.0.2", "TestAgent-Updated");
        assertNotNull(refreshResponse.getAccessToken());
        assertNotNull(refreshResponse.getRefreshToken());

        // New access token must be cryptographically valid
        JwtUserClaims claims = jwtTokenVerifier.verify(refreshResponse.getAccessToken());
        assertNotNull(claims.jti());
        assertEquals("refresh.user@shareride.com", claims.email());

        // Old token should be revoked from Valkey
        Optional<RefreshTokenData> oldTokenData =
                refreshTokenCacheService.getRefreshTokenData(initialRefreshToken);
        assertTrue(oldTokenData.isEmpty(), "Old refresh token must be revoked from Valkey");

        // New token should be present in Valkey
        Optional<RefreshTokenData> newTokenData =
                refreshTokenCacheService.getRefreshTokenData(refreshResponse.getRefreshToken());
        assertTrue(newTokenData.isPresent(), "New refresh token must be saved in Valkey");
    }

    @Test
    @DisplayName("Logout: Revokes JWT access token in Valkey and deletes refresh token")
    void testLogoutRevokesAccessTokenAndRefreshToken() {
        RegisterRequest registerRequest =
                RegisterRequest.builder()
                        .email("logout.user@shareride.com")
                        .password("CorrectPassword123")
                        .phoneNumber("+84988999333")
                        .roleCode("ROLE_RIDER")
                        .build();
        authService.register(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("logout.user@shareride.com")
                        .password("CorrectPassword123")
                        .build();
        AuthResponse loginResponse = authService.login(loginRequest, "127.0.0.1", "TestAgent");

        // Before logout, token is valid
        JwtUserClaims claims = jwtTokenVerifier.verify(loginResponse.getAccessToken());
        assertNotNull(claims);

        // Perform logout
        authService.logout(loginResponse.getAccessToken(), loginResponse.getRefreshToken());

        // After logout, token must be rejected because JTI is blacklisted
        assertThrows(
                UnauthorizedException.class,
                () -> jwtTokenVerifier.verify(loginResponse.getAccessToken()),
                "Revoked access token must be rejected on verification");

        // Refresh token must be removed from Valkey
        Optional<RefreshTokenData> rtData =
                refreshTokenCacheService.getRefreshTokenData(loginResponse.getRefreshToken());
        assertTrue(rtData.isEmpty(), "Refresh token must be removed from Valkey after logout");
    }

    @Test
    @DisplayName(
            "Revoke Session: Revoking session marks DB session REVOKED and removes from Valkey")
    void testRevokeSession() {
        RegisterRequest registerRequest =
                RegisterRequest.builder()
                        .email("revoke.user@shareride.com")
                        .password("CorrectPassword123")
                        .phoneNumber("+84988999222")
                        .build();
        AccountResponse account = authService.register(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("revoke.user@shareride.com")
                        .password("CorrectPassword123")
                        .deviceId("device-to-revoke")
                        .build();
        AuthResponse loginResponse = authService.login(loginRequest, "127.0.0.1", "TestAgent");

        // Revoke session
        authService.revokeSession(loginResponse.getSessionId(), account.getAccountId());

        // Verify DB session
        Session session = sessionRepository.findById(loginResponse.getSessionId()).orElseThrow();
        assertEquals(SessionStatus.REVOKED, session.getStatus());

        // Verify Valkey token is revoked
        Optional<RefreshTokenData> tokenData =
                refreshTokenCacheService.getRefreshTokenData(loginResponse.getRefreshToken());
        assertTrue(tokenData.isEmpty(), "Refresh token must be removed from Valkey");
    }

    @Test
    @DisplayName("Validation: Should reject duplicate email registration")
    void testDuplicateEmailRegistration() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("duplicate@shareride.com")
                        .password("Password123")
                        .phoneNumber("+84988111999")
                        .build();
        authService.register(request);

        RegisterRequest duplicateRequest =
                RegisterRequest.builder()
                        .email("duplicate@shareride.com")
                        .password("Password123")
                        .phoneNumber("+84988222999")
                        .build();

        assertThrows(ConflictException.class, () -> authService.register(duplicateRequest));
    }
}
