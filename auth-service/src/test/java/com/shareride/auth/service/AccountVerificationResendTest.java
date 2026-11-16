package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthVerificationConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.ResendVerificationRequest;
import com.shareride.auth.dto.ResendVerificationResponse;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.service.impl.AccountVerificationServiceImpl;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.ResourceNotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Verification Resend Tests")
class AccountVerificationResendTest {

    @Mock private AccountRepository accountRepository;

    @Mock private AccountVerificationRepository accountVerificationRepository;

    @Mock private AccountManagementService accountManagementService;

    @Mock private AuthEventPublisher authEventPublisher;

    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Mock private ValueOperations<String, Object> valueOperations;

    private AccountVerificationService accountVerificationService;

    @BeforeEach
    void setUp() {
        accountVerificationService =
                new AccountVerificationServiceImpl(
                        accountRepository,
                        accountVerificationRepository,
                        accountManagementService,
                        authEventPublisher,
                        redisTemplate);
    }

    @Test
    @DisplayName("Resend: Rejects request within 60-second cooldown with HTTP 429")
    void testResendRejectsDuringCooldown() {
        final String email = "user@example.com";
        final UUID accountId = UUID.randomUUID();

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(accountId);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        final String cooldownKey = AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + accountId;
        when(valueOperations.get(cooldownKey)).thenReturn("1");
        when(redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS)).thenReturn(45L);

        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(email).build();

        final RateLimitExceededException exception =
                assertThrows(
                        RateLimitExceededException.class,
                        () -> accountVerificationService.resendVerification(request));

        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("Resend: Rejects after 5 resends in one hour with HTTP 400")
    void testResendRejectsAfterHourlyLimit() {
        final String email = "user@example.com";
        final UUID accountId = UUID.randomUUID();

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(accountId);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        final String cooldownKey = AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + accountId;
        when(valueOperations.get(cooldownKey)).thenReturn(null);

        final String hourlyKey =
                AuthConstants.CacheKey.VERIFICATION_RESEND_COUNT_PREFIX
                        + accountId
                        + ":"
                        + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        when(valueOperations.get(hourlyKey)).thenReturn(5);

        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(email).build();

        final BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> accountVerificationService.resendVerification(request));

        assertEquals(AuthResponseCode.RESEND_HOURLY_LIMIT_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("Resend: Invalidates previous PENDING codes to EXPIRED")
    void testResendInvalidatesPreviousCodes() {
        final String email = "user@example.com";
        final UUID accountId = UUID.randomUUID();

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(accountId);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        final String cooldownKey = AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + accountId;
        when(valueOperations.get(cooldownKey)).thenReturn(null);

        final String hourlyKey =
                AuthConstants.CacheKey.VERIFICATION_RESEND_COUNT_PREFIX
                        + accountId
                        + ":"
                        + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        when(valueOperations.get(hourlyKey)).thenReturn(2);
        when(accountVerificationRepository.expireAllPendingByAccountId(
                        eq(accountId),
                        eq(VerificationStatus.PENDING),
                        eq(VerificationStatus.EXPIRED)))
                .thenReturn(1);

        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(email).build();

        final ResendVerificationResponse response =
                accountVerificationService.resendVerification(request);

        assertNotNull(response);
        assertEquals(email, response.getEmail());
        assertEquals(
                AuthVerificationConstants.COOLDOWN_DURATION.getSeconds(),
                response.getCooldownSeconds());

        verify(accountVerificationRepository)
                .expireAllPendingByAccountId(
                        accountId, VerificationStatus.PENDING, VerificationStatus.EXPIRED);
    }

    @Test
    @DisplayName("Resend: Successfully generates new code and dispatches event")
    void testResendSuccessfullyDispatchesNewCode() {
        final String email = "user@example.com";
        final UUID accountId = UUID.randomUUID();

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(accountId);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        final String cooldownKey = AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + accountId;
        when(valueOperations.get(cooldownKey)).thenReturn(null);

        final String hourlyKey =
                AuthConstants.CacheKey.VERIFICATION_RESEND_COUNT_PREFIX
                        + accountId
                        + ":"
                        + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        when(valueOperations.get(hourlyKey)).thenReturn(1);
        when(accountVerificationRepository.expireAllPendingByAccountId(any(), any(), any()))
                .thenReturn(1);

        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(email).build();

        final ResendVerificationResponse response =
                accountVerificationService.resendVerification(request);

        assertNotNull(response);
        assertEquals(email, response.getEmail());

        verify(accountVerificationRepository).save(any(AccountVerification.class));
        verify(authEventPublisher)
                .publishAccountEvent(anyString(), eq("ACCOUNT_VERIFICATION"), any());
        verify(valueOperations)
                .set(eq(cooldownKey), eq("1"), eq(AuthVerificationConstants.COOLDOWN_DURATION));
        verify(valueOperations).increment(eq(hourlyKey));
        verify(redisTemplate).expire(eq(hourlyKey), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Resend: Throws ResourceNotFoundException for nonexistent email")
    void testResendThrowsForNonexistentEmail() {
        final String email = "nonexistent@example.com";

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(email).build();

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountVerificationService.resendVerification(request));
    }

    @Test
    @DisplayName("Resend: Throws ConflictException for already ACTIVE account")
    void testResendThrowsForActiveAccount() {
        final String email = "active@example.com";
        final UUID accountId = UUID.randomUUID();

        final Account account =
                Account.builder()
                        .email(email)
                        .status(AccountStatus.ACTIVE)
                        .passwordHash("$argon2id$...")
                        .build();
        account.setId(accountId);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));

        final ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(email).build();

        final ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> accountVerificationService.resendVerification(request));

        assertEquals(AuthResponseCode.ACCOUNT_ALREADY_ACTIVE, exception.getCode());
    }
}
