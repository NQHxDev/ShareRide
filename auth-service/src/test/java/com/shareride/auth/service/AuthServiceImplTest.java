package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Session;
import com.shareride.auth.domain.SessionStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.impl.AuthServiceImpl;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private RefreshTokenCacheService refreshTokenCacheService;
    @Mock private JwtService jwtService;
    @Mock private AuditLogService auditLogService;
    @Mock private RegistrationService registrationService;
    @Mock private SessionManagementService sessionManagementService;
    @Mock private AccountManagementService accountManagementService;
    @Mock private LoginRateLimiterService loginRateLimiterService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
        PasswordUtils.setSecret("test_argon2id_secret_pepper_12345");
        authService =
                new AuthServiceImpl(
                        accountRepository,
                        refreshTokenCacheService,
                        jwtService,
                        auditLogService,
                        registrationService,
                        sessionManagementService,
                        accountManagementService,
                        loginRateLimiterService);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Register: delegates registration to RegistrationService")
    void testRegisterDelegatesToRegistrationService() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("rider@shareride.com")
                        .password("SuperSecretPass123!")
                        .phoneNumber("+84988111222")
                        .roleCode("ROLE_RIDER")
                        .build();

        AccountResponse expectedResponse =
                AccountResponse.builder()
                        .accountId(UUID.randomUUID())
                        .email("rider@shareride.com")
                        .phoneNumber("+84988111222")
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of("ROLE_RIDER"))
                        .build();

        when(registrationService.register(request)).thenReturn(expectedResponse);

        AccountResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("rider@shareride.com", response.getEmail());
        verify(registrationService).register(request);
    }

    @Test
    @DisplayName("Login: Success when password matches Argon2id hash")
    void testLoginSuccessWithArgon2id() {
        String rawPassword = "ValidPassword123";
        String argon2idHash = PasswordUtils.hashPassword(rawPassword);
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Account account =
                Account.builder()
                        .email("active.user@shareride.com")
                        .phoneNumber("+84988333444")
                        .passwordHash(argon2idHash)
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of())
                        .build();
        account.setId(accountId);

        Session session =
                Session.builder()
                        .accountId(accountId)
                        .status(SessionStatus.ACTIVE)
                        .lastActiveAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
        session.setId(sessionId);

        AccountResponse accountResponse =
                AccountResponse.builder()
                        .accountId(accountId)
                        .email("active.user@shareride.com")
                        .build();

        when(accountRepository.findByEmailAndDeletedFalse("active.user@shareride.com"))
                .thenReturn(Optional.of(account));
        when(sessionManagementService.createSession(any(), any(), any(), any()))
                .thenReturn(session);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("mock-access-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(java.time.Duration.ofMinutes(15));
        when(accountManagementService.toAccountResponse(account)).thenReturn(accountResponse);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("active.user@shareride.com")
                        .password(rawPassword)
                        .deviceId("device-123")
                        .deviceName("Pixel 8")
                        .deviceType("MOBILE")
                        .build();

        var authResponse = authService.login(loginRequest, "127.0.0.1", "ShareRideApp/1.0");

        assertNotNull(authResponse);
        assertEquals("mock-access-token", authResponse.getAccessToken());
        verify(refreshTokenCacheService).saveRefreshToken(any(), any(), any(), any());
        verify(loginRateLimiterService)
                .resetLoginAttempts("127.0.0.1", "active.user@shareride.com");
    }

    @Test
    @DisplayName(
            "Login: Throws UnauthorizedException when wrong password is provided against Argon2id"
                    + " hash and records login failure")
    void testLoginFailureWrongPassword() {
        String correctPassword = "CorrectPassword123";
        String argon2idHash = PasswordUtils.hashPassword(correctPassword);

        Account account =
                Account.builder()
                        .email("user@shareride.com")
                        .phoneNumber("+84988555666")
                        .passwordHash(argon2idHash)
                        .status(AccountStatus.ACTIVE)
                        .build();
        account.setId(UUID.randomUUID());

        when(accountRepository.findByEmailAndDeletedFalse("user@shareride.com"))
                .thenReturn(Optional.of(account));
        when(loginRateLimiterService.recordLoginFailure("127.0.0.1", "user@shareride.com"))
                .thenReturn(1);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("user@shareride.com")
                        .password("WrongPassword999")
                        .build();

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(loginRequest, "127.0.0.1", "ShareRideApp/1.0"));
        verify(loginRateLimiterService).recordLoginFailure("127.0.0.1", "user@shareride.com");
    }

    @Test
    @DisplayName("Login: Throws RateLimitExceededException when IP is blocked (5 failures in 15m)")
    void testLoginFailureIpBlocked() {
        when(loginRateLimiterService.isIpBlocked("192.168.1.100")).thenReturn(true);

        LoginRequest loginRequest =
                LoginRequest.builder().email("any@shareride.com").password("Password123").build();

        assertThrows(
                RateLimitExceededException.class,
                () -> authService.login(loginRequest, "192.168.1.100", "ShareRideApp/1.0"));
    }

    @Test
    @DisplayName("Login: Throws ForbiddenException when account status is already LOCKED")
    void testLoginAccountAlreadyLocked() {
        Account account =
                Account.builder()
                        .email("locked@shareride.com")
                        .phoneNumber("+84988999888")
                        .passwordHash("dummyHash")
                        .status(AccountStatus.LOCKED)
                        .build();
        account.setId(UUID.randomUUID());

        when(accountRepository.findByEmailAndDeletedFalse("locked@shareride.com"))
                .thenReturn(Optional.of(account));

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("locked@shareride.com")
                        .password("Password123")
                        .build();

        assertThrows(
                ForbiddenException.class,
                () -> authService.login(loginRequest, "127.0.0.1", "ShareRideApp/1.0"));
    }

    @Test
    @DisplayName("Login: Locks account and throws ForbiddenException on 10th consecutive failure")
    void testLoginFailureLocksAccountAfterMaxAttempts() {
        String correctPassword = "CorrectPassword123";
        String argon2idHash = PasswordUtils.hashPassword(correctPassword);

        Account account =
                Account.builder()
                        .email("target@shareride.com")
                        .phoneNumber("+84988777888")
                        .passwordHash(argon2idHash)
                        .status(AccountStatus.ACTIVE)
                        .build();
        account.setId(UUID.randomUUID());

        when(accountRepository.findByEmailAndDeletedFalse("target@shareride.com"))
                .thenReturn(Optional.of(account));
        when(loginRateLimiterService.recordLoginFailure("127.0.0.1", "target@shareride.com"))
                .thenReturn(10); // 10th failure

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("target@shareride.com")
                        .password("WrongPassword123")
                        .build();

        assertThrows(
                ForbiddenException.class,
                () -> authService.login(loginRequest, "127.0.0.1", "ShareRideApp/1.0"));

        assertEquals(AccountStatus.LOCKED, account.getStatus());
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("getAccountById: delegates to AccountManagementService")
    void testGetAccountByIdDelegates() {
        UUID accountId = UUID.randomUUID();
        AccountResponse expected =
                AccountResponse.builder().accountId(accountId).email("self@shareride.com").build();

        when(accountManagementService.getAccountById(accountId)).thenReturn(expected);

        AccountResponse actual = authService.getAccountById(accountId);
        assertNotNull(actual);
        assertEquals(accountId, actual.getAccountId());
        verify(accountManagementService).getAccountById(accountId);
    }

    @Test
    @DisplayName("getActiveSessions: delegates to SessionManagementService")
    void testGetActiveSessionsDelegates() {
        UUID accountId = UUID.randomUUID();
        when(sessionManagementService.getActiveSessions(accountId)).thenReturn(java.util.List.of());

        var sessions = authService.getActiveSessions(accountId);
        assertNotNull(sessions);
        verify(sessionManagementService).getActiveSessions(accountId);
    }

    @Test
    @DisplayName("revokeSession: delegates to SessionManagementService")
    void testRevokeSessionDelegates() {
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        authService.revokeSession(sessionId, accountId);
        verify(sessionManagementService).revokeSession(sessionId, accountId);
    }

    @Test
    @DisplayName("softDeleteAccount: delegates to AccountManagementService")
    void testSoftDeleteAccountDelegates() {
        UUID accountId = UUID.randomUUID();
        authService.softDeleteAccount(accountId, "admin");
        verify(accountManagementService).softDeleteAccount(accountId, "admin");
    }

    @Test
    @DisplayName("Login: Throws UnauthorizedException when account does not exist")
    void testLoginAccountNotFound() {
        LoginRequest req =
                LoginRequest.builder().email("nonexistent@shareride.com").password("pass").build();
        when(accountRepository.findByEmailAndDeletedFalse("nonexistent@shareride.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(req, "127.0.0.1", "ShareRide/1.0"));
        verify(loginRateLimiterService)
                .recordLoginFailure("127.0.0.1", "nonexistent@shareride.com");
        verify(auditLogService).logSecurityEvent(any());
    }

    @Test
    @DisplayName("Login: Throws UnauthorizedException when account is inactive")
    void testLoginAccountInactive() {
        String rawPassword = "ValidPassword123";
        String argon2idHash = PasswordUtils.hashPassword(rawPassword);
        Account account =
                Account.builder()
                        .email("inactive@shareride.com")
                        .passwordHash(argon2idHash)
                        .status(AccountStatus.INACTIVE)
                        .build();
        account.setId(UUID.randomUUID());

        when(accountRepository.findByEmailAndDeletedFalse("inactive@shareride.com"))
                .thenReturn(Optional.of(account));

        LoginRequest req =
                LoginRequest.builder()
                        .email("inactive@shareride.com")
                        .password(rawPassword)
                        .build();

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(req, "127.0.0.1", "ShareRide/1.0"));
    }

    @Test
    @DisplayName("RefreshToken: Successfully rotates token and returns updated AuthResponse")
    void testRefreshTokenSuccess() {
        String token = "old-refresh-token";
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Session session =
                Session.builder().accountId(accountId).status(SessionStatus.ACTIVE).build();
        session.setId(sessionId);

        Account account =
                Account.builder().email("rider@shareride.com").status(AccountStatus.ACTIVE).build();
        account.setId(accountId);

        AccountResponse accountResponse = AccountResponse.builder().accountId(accountId).build();

        when(sessionManagementService.refreshSession(token, "127.0.0.1")).thenReturn(session);
        when(accountManagementService.getActiveAccount(accountId)).thenReturn(account);
        when(jwtService.generateAccessToken(account, session)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(java.time.Duration.ofMinutes(15));
        when(accountManagementService.toAccountResponse(account)).thenReturn(accountResponse);

        var resp = authService.refreshToken(token, "127.0.0.1", "App/1.0");

        assertNotNull(resp);
        assertEquals("new-access-token", resp.getAccessToken());
        verify(refreshTokenCacheService)
                .saveRefreshToken(eq(sessionId), eq(accountId), any(), any());
        verify(auditLogService).logSecurityEvent(any());
    }

    @Test
    @DisplayName("Logout: Revokes access token, terminates session, and logs audit event")
    void testLogoutSuccess() {
        UserContextHolder.setContext(
                com.shareride.common.context.UserContext.builder().userId("user-123").build());

        authService.logout("mock-access-token", "mock-refresh-token");

        verify(jwtService).revokeToken("mock-access-token");
        verify(sessionManagementService).terminateSession("mock-refresh-token");
        verify(auditLogService).logSecurityEvent(any());

        // Logout with null/blank access token and empty user context
        UserContextHolder.clear();
        authService.logout(null, "mock-refresh-token-2");
        verify(sessionManagementService).terminateSession("mock-refresh-token-2");
    }

    @Test
    @DisplayName("getJwks: delegates to JwtService")
    void testGetJwksDelegates() {
        when(jwtService.getJwks()).thenReturn(java.util.Map.of("keys", java.util.List.of()));
        var jwks = authService.getJwks();
        assertNotNull(jwks);
        verify(jwtService).getJwks();
    }
}
