package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.RoleRepository;
import com.shareride.auth.repository.SessionRepository;
import com.shareride.auth.service.impl.AuthServiceImpl;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.outbox.OutboxService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenCacheService refreshTokenCacheService;
    @Mock private JwtService jwtService;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogService auditLogService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PasswordUtils.setSecret("test_argon2id_secret_pepper_12345");
        authService =
                new AuthServiceImpl(
                        accountRepository,
                        sessionRepository,
                        roleRepository,
                        refreshTokenCacheService,
                        jwtService,
                        outboxService,
                        new StaticListableBeanFactory()
                                .getBeanProvider(
                                        com.shareride.common.cache.DistributedLockService.class),
                        auditLogService);
    }

    @Test
    @DisplayName("Register: Password must be hashed using Argon2id format ($argon2id$v=19$...)")
    void testRegisterWithArgon2id() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("rider@shareride.com")
                        .password("SuperSecretPass123!")
                        .phoneNumber("+84988111222")
                        .roleCode("ROLE_RIDER")
                        .build();

        Role riderRole = Role.builder().roleCode(RoleCode.ROLE_RIDER).roleName("Rider").build();
        when(accountRepository.existsByEmail("rider@shareride.com")).thenReturn(false);
        when(accountRepository.existsByPhoneNumber("+84988111222")).thenReturn(false);
        when(roleRepository.findByRoleCode(RoleCode.ROLE_RIDER)).thenReturn(Optional.of(riderRole));

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture()))
                .thenAnswer(
                        invocation -> {
                            Account a = invocation.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });

        AccountResponse response = authService.register(request);

        assertNotNull(response);
        Account savedAccount = accountCaptor.getValue();
        assertNotNull(savedAccount.getPasswordHash());
        assertTrue(
                savedAccount.getPasswordHash().startsWith("$argon2id$v=19$m=16384,t=2,p=1$"),
                "Password must be hashed with Argon2id parameters");
        assertTrue(
                PasswordUtils.verifyPassword("SuperSecretPass123!", savedAccount.getPasswordHash()),
                "Argon2id password encoder must verify raw password correctly");
    }

    @Test
    @DisplayName("Login: Success when password matches Argon2id hash")
    void testLoginSuccessWithArgon2id() {
        String rawPassword = "ValidPassword123";
        String argon2idHash = PasswordUtils.hashPassword(rawPassword);

        Account account =
                Account.builder()
                        .email("active.user@shareride.com")
                        .phoneNumber("+84988333444")
                        .passwordHash(argon2idHash)
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of())
                        .build();
        account.setId(UUID.randomUUID());

        when(accountRepository.findByEmailAndDeletedFalse("active.user@shareride.com"))
                .thenReturn(Optional.of(account));
        when(sessionRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            com.shareride.auth.domain.Session s = invocation.getArgument(0);
                            s.setId(UUID.randomUUID());
                            return s;
                        });
        when(jwtService.generateAccessToken(any(), any())).thenReturn("mock-access-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(java.time.Duration.ofMinutes(15));

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
}
