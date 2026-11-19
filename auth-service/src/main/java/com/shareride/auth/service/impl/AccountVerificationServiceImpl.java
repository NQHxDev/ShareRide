package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthVerificationConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.AccountResponseDTO;
import com.shareride.auth.dto.ResendVerificationRequest;
import com.shareride.auth.dto.ResendVerificationResponse;
import com.shareride.auth.dto.VerifyEmailRequest;
import com.shareride.auth.publisher.AuthEventPublisher;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.service.AccountManagementService;
import com.shareride.auth.service.AccountVerificationService;
import com.shareride.auth.util.AlphanumericCodeGenerator;
import com.shareride.auth.util.HashingUtils;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.util.UuidUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation của AccountVerificationService quản lý xác thực email với SHA-256 hash matching,
 * failed attempts tracking, và 15-minute expiration enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountVerificationServiceImpl implements AccountVerificationService {

    private final AccountRepository accountRepository;
    private final AccountVerificationRepository accountVerificationRepository;
    private final AccountManagementService accountManagementService;
    private final AuthEventPublisher authEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public AccountResponseDTO verifyEmail(final VerifyEmailRequest request) {
        log.info("Verifying email for account: {}", request.getEmail());

        final Account account =
                accountRepository
                        .findByEmail(request.getEmail())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthResponseCode.ACCOUNT_NOT_FOUND,
                                                AuthMessage.ACCOUNT_NOT_FOUND));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new ConflictException(
                    AuthResponseCode.ACCOUNT_ALREADY_ACTIVE, AuthMessage.ACCOUNT_ALREADY_ACTIVE);
        }

        final AccountVerification verification =
                accountVerificationRepository
                        .findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                                account.getId(), VerificationStatus.PENDING)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "No pending verification found for account: {}",
                                            account.getId());
                                    return new BadRequestException(
                                            AuthResponseCode.VERIFICATION_CODE_EXPIRED,
                                            AuthMessage.VERIFICATION_CODE_EXPIRED);
                                });

        if (verification.getExpiresAt().isBefore(Instant.now())) {
            log.warn(
                    "Verification code expired for account: {} at {}",
                    account.getId(),
                    verification.getExpiresAt());
            verification.setStatus(VerificationStatus.EXPIRED);
            accountVerificationRepository.save(verification);
            throw new BadRequestException(
                    AuthResponseCode.VERIFICATION_CODE_EXPIRED,
                    AuthMessage.VERIFICATION_CODE_EXPIRED);
        }

        final String normalizedInput = request.getCode().trim().toUpperCase(Locale.ROOT);
        final String inputHash = HashingUtils.sha256Hex(normalizedInput);

        if (!HashingUtils.constantTimeEquals(inputHash, verification.getVerificationCodeHash())) {
            log.warn(
                    "Invalid verification code for account: {}. Failed attempts: {}",
                    account.getId(),
                    verification.getFailedAttempts() + 1);

            verification.setFailedAttempts(verification.getFailedAttempts() + 1);

            if (verification.getFailedAttempts() >= AuthVerificationConstants.MAX_FAILED_ATTEMPTS) {
                verification.setStatus(VerificationStatus.MAX_ATTEMPTS_EXCEEDED);
                log.warn("Max verification attempts exceeded for account: {}", account.getId());
            }

            accountVerificationRepository.save(verification);

            throw new BadRequestException(
                    AuthResponseCode.INVALID_VERIFICATION_CODE,
                    AuthMessage.INVALID_VERIFICATION_CODE);
        }

        log.info("Verification code matched for account: {}. Activating account.", account.getId());

        verification.setStatus(VerificationStatus.VERIFIED);
        accountVerificationRepository.save(verification);

        account.setStatus(AccountStatus.ACTIVE);
        final Account savedAccount = accountRepository.save(account);

        log.info("Account activated successfully: {}", savedAccount.getId());

        dispatchWelcomeNotification(savedAccount);

        return accountManagementService.toAccountResponse(savedAccount);
    }

    @Override
    @Transactional
    public ResendVerificationResponse resendVerification(final ResendVerificationRequest request) {
        log.info("Processing resend verification request for email: {}", request.getEmail());

        final Account account =
                accountRepository
                        .findByEmail(request.getEmail())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                AuthResponseCode.ACCOUNT_NOT_FOUND,
                                                AuthMessage.ACCOUNT_NOT_FOUND));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new ConflictException(
                    AuthResponseCode.ACCOUNT_ALREADY_ACTIVE, AuthMessage.ACCOUNT_ALREADY_ACTIVE);
        }

        final String cooldownKey =
                AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + account.getId();
        final Object cooldownValue = redisTemplate.opsForValue().get(cooldownKey);

        if (cooldownValue != null) {
            final Long remainingSeconds = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            log.warn(
                    "Cooldown active for account: {}. Remaining: {} seconds",
                    account.getId(),
                    remainingSeconds);
            throw new RateLimitExceededException(
                    String.format(
                            "Vui lòng chờ %d giây trước khi gửi lại mã xác thực",
                            remainingSeconds != null ? remainingSeconds : 60));
        }

        final String hourlyKey =
                AuthConstants.CacheKey.VERIFICATION_RESEND_COUNT_PREFIX
                        + account.getId()
                        + ":"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        final Object hourlyCount = redisTemplate.opsForValue().get(hourlyKey);

        if (hourlyCount != null
                && ((Number) hourlyCount).intValue()
                        >= AuthVerificationConstants.HOURLY_RESEND_LIMIT) {
            log.warn("Hourly resend limit exceeded for account: {}", account.getId());
            throw new BadRequestException(
                    AuthResponseCode.RESEND_HOURLY_LIMIT_EXCEEDED,
                    AuthMessage.RESEND_HOURLY_LIMIT_EXCEEDED);
        }

        log.info("Invalidating previous PENDING verifications for account: {}", account.getId());
        accountVerificationRepository.expireAllPendingByAccountId(
                account.getId(), VerificationStatus.PENDING, VerificationStatus.EXPIRED);

        final String newCode = AlphanumericCodeGenerator.generate();
        final String codeHash = HashingUtils.sha256Hex(newCode);

        final AccountVerification newVerification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(codeHash)
                        .expiresAt(Instant.now().plus(AuthVerificationConstants.EXPIRY_DURATION))
                        .failedAttempts(0)
                        .status(VerificationStatus.PENDING)
                        .build();

        accountVerificationRepository.save(newVerification);

        log.info("Generated new verification code for account: {}", account.getId());

        redisTemplate
                .opsForValue()
                .set(cooldownKey, "1", AuthVerificationConstants.COOLDOWN_DURATION);

        redisTemplate.opsForValue().increment(hourlyKey);
        redisTemplate.expire(hourlyKey, Duration.ofHours(1));

        log.info("Set cooldown and incremented hourly counter for account: {}", account.getId());

        dispatchVerificationEmail(account, newCode);

        return ResendVerificationResponse.builder()
                .email(account.getEmail())
                .cooldownSeconds((int) AuthVerificationConstants.COOLDOWN_DURATION.getSeconds())
                .message(AuthMessage.VERIFICATION_CODE_RESENT)
                .build();
    }

    private void dispatchWelcomeNotification(final Account account) {
        log.info("Dispatching WELCOME notification for account: {}", account.getId());

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userName", account.getEmail());

        final Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UuidUtils.v7().toString());
        payload.put("recipientEmail", account.getEmail());
        payload.put("notificationType", "WELCOME");
        payload.put("locale", "vi-VN");
        payload.put("parameters", parameters);

        authEventPublisher.publishAccountEvent(account.getId().toString(), "WELCOME", payload);

        log.info("WELCOME notification dispatched for account: {}", account.getId());
    }

    private void dispatchVerificationEmail(final Account account, final String verificationCode) {
        log.info("Dispatching ACCOUNT_VERIFICATION email for account: {}", account.getId());

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userName", account.getEmail());
        parameters.put("verificationCode", verificationCode);
        parameters.put("expiresInMinutes", AuthVerificationConstants.EXPIRY_DURATION.toMinutes());

        final Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UuidUtils.v7().toString());
        payload.put("recipientEmail", account.getEmail());
        payload.put("notificationType", "ACCOUNT_VERIFICATION");
        payload.put("locale", "vi-VN");
        payload.put("parameters", parameters);

        authEventPublisher.publishAccountEvent(
                account.getId().toString(), "ACCOUNT_VERIFICATION", payload);

        log.info("ACCOUNT_VERIFICATION email dispatched for account: {}", account.getId());
    }
}
