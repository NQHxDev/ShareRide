package com.shareride.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.exception.AccountPendingVerificationException;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl - Unverified Account Login Protection Tests")
class AuthServiceImplUnverifiedLoginTest {

    @Mock private AccountRepository accountRepository;

    @Mock private RefreshTokenCacheService refreshTokenCacheService;

    @Mock private JwtService jwtService;

    @Mock private RegistrationService registrationService;

    @Mock private SessionManagementService sessionManagementService;

    @Mock private AccountManagementService accountManagementService;

    @Mock private LoginRateLimiterService loginRateLimiterService;

    @Mock private com.shareride.common.logging.audit.AuditLogService auditLogService;

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private com.shareride.auth.service.impl.AuthServiceImpl authService;

    private Account pendingAccount;
    private LoginRequest loginRequest;
    private static final String TEST_EMAIL = "pending@example.com";
    private static final String TEST_PASSWORD = "SecurePassword123!";
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_USER_AGENT = "Mozilla/5.0";

    @BeforeEach
    void setUp() {
        PasswordUtils.setSecret("test_argon2id_secret_pepper_for_unit_tests_only");

        pendingAccount = new Account();
        pendingAccount.setId(UUID.randomUUID());
        pendingAccount.setEmail(TEST_EMAIL);
        pendingAccount.setPasswordHash(PasswordUtils.hashPassword(TEST_PASSWORD));
        pendingAccount.setStatus(AccountStatus.PENDING_VERIFICATION);
        pendingAccount.setCreatedAt(Instant.now());
        pendingAccount.setUpdatedAt(Instant.now());

        loginRequest = new LoginRequest();
        loginRequest.setEmail(TEST_EMAIL);
        loginRequest.setPassword(TEST_PASSWORD);

        org.mockito.Mockito.lenient()
                .when(loginRateLimiterService.isIpBlocked(anyString()))
                .thenReturn(false);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        com.shareride.common.context.UserContextHolder.clear();
    }

    @Test
    @DisplayName(
            "Should throw AccountPendingVerificationException when logging in with valid"
                    + " credentials but unverified account")
    void shouldThrowAccountPendingVerificationException_whenAccountIsUnverified() {
        when(accountRepository.findByEmailAndDeletedFalse(TEST_EMAIL.toLowerCase()))
                .thenReturn(Optional.of(pendingAccount));

        String cooldownKey = "auth:verify:cooldown:" + pendingAccount.getId();
        when(valueOperations.get(cooldownKey)).thenReturn("1");
        when(redisTemplate.getExpire(eq(cooldownKey), any())).thenReturn(45L);

        assertThatThrownBy(() -> authService.login(loginRequest, TEST_IP, TEST_USER_AGENT))
                .isInstanceOf(AccountPendingVerificationException.class)
                .hasMessageContaining(AuthMessage.ACCOUNT_PENDING_VERIFICATION)
                .satisfies(
                        exception -> {
                            AccountPendingVerificationException ex =
                                    (AccountPendingVerificationException) exception;
                            assertThat(ex.getEmail()).isEqualTo(TEST_EMAIL.toLowerCase());
                            assertThat(ex.getRemainingCooldownSeconds()).isEqualTo(45);
                            assertThat(ex.getResponseCode())
                                    .isEqualTo(AuthResponseCode.ACCOUNT_PENDING_VERIFICATION);
                        });

        verify(sessionManagementService, never())
                .createSession(any(), any(), anyString(), anyString());
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName(
            "Should throw AccountPendingVerificationException with zero cooldown when cooldown key"
                    + " does not exist")
    void
            shouldThrowAccountPendingVerificationException_withZeroCooldown_whenNoActiveResendCooldown() {
        when(accountRepository.findByEmailAndDeletedFalse(TEST_EMAIL.toLowerCase()))
                .thenReturn(Optional.of(pendingAccount));

        String cooldownKey = "auth:verify:cooldown:" + pendingAccount.getId();
        when(valueOperations.get(cooldownKey)).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginRequest, TEST_IP, TEST_USER_AGENT))
                .isInstanceOf(AccountPendingVerificationException.class)
                .satisfies(
                        exception -> {
                            AccountPendingVerificationException ex =
                                    (AccountPendingVerificationException) exception;
                            assertThat(ex.getRemainingCooldownSeconds()).isEqualTo(0);
                        });
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when password is invalid for pending account")
    void shouldThrowUnauthorizedException_whenPasswordIsInvalid_forPendingAccount() {
        when(accountRepository.findByEmailAndDeletedFalse(TEST_EMAIL.toLowerCase()))
                .thenReturn(Optional.of(pendingAccount));

        LoginRequest wrongPasswordRequest = new LoginRequest();
        wrongPasswordRequest.setEmail(TEST_EMAIL);
        wrongPasswordRequest.setPassword("WrongPassword!");

        assertThatThrownBy(() -> authService.login(wrongPasswordRequest, TEST_IP, TEST_USER_AGENT))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining(AuthMessage.INVALID_EMAIL_OR_PASSWORD);

        verify(sessionManagementService, never())
                .createSession(any(), any(), anyString(), anyString());
    }
}
