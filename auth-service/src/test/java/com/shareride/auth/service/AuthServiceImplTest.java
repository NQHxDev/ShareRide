package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
                        accountManagementService);
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
    }

    @Test
    @DisplayName(
            "Login: Throws UnauthorizedException when wrong password is provided against Argon2id"
                    + " hash")
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

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .email("user@shareride.com")
                        .password("WrongPassword999")
                        .build();

        assertThrows(
                UnauthorizedException.class,
                () -> authService.login(loginRequest, "127.0.0.1", "ShareRideApp/1.0"));
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
}
