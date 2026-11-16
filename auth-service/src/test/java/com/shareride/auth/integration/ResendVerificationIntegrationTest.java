package com.shareride.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.constant.AuthVerificationConstants;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.ResendVerificationRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.auth.util.AlphanumericCodeGenerator;
import com.shareride.auth.util.HashingUtils;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test validating verification code resend flow with Valkey TTL countdown, 60s cooldown
 * enforcement, hourly rate limiting, and code invalidation per T037, FR-008, FR-009.
 */
@AutoConfigureMockMvc
@DisplayName("Integration Test: Resend Verification Code")
class ResendVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private AccountRepository accountRepository;

    @Autowired private AccountVerificationRepository verificationRepository;

    @Autowired private StringRedisTemplate redisTemplate;

    private Account pendingAccount;
    private static final String TEST_EMAIL = "resend-test@example.com";
    private static final String TEST_PASSWORD = "SecurePassword123!";
    private static final String RESEND_URL = "/api/auth/v1/resend-verification";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        verificationRepository.deleteAll();

        pendingAccount = new Account();
        pendingAccount.setId(UuidUtils.v7());
        pendingAccount.setEmail(TEST_EMAIL);
        pendingAccount.setPhoneNumber("+84901234567");
        pendingAccount.setPasswordHash(PasswordUtils.hashPassword(TEST_PASSWORD));
        pendingAccount.setStatus(AccountStatus.PENDING_VERIFICATION);
        pendingAccount.setCreatedAt(Instant.now());
        pendingAccount.setUpdatedAt(Instant.now());
        pendingAccount.setDeleted(false);
        accountRepository.save(pendingAccount);

        String initialCode = AlphanumericCodeGenerator.generate();
        String codeHash = HashingUtils.sha256Hex(initialCode);

        AccountVerification verification = new AccountVerification();
        verification.setId(UuidUtils.v7());
        verification.setAccountId(pendingAccount.getId());
        verification.setVerificationCodeHash(codeHash);
        verification.setExpiresAt(Instant.now().plus(AuthVerificationConstants.EXPIRY_DURATION));
        verification.setFailedAttempts(0);
        verification.setStatus(VerificationStatus.PENDING);
        verification.setCreatedAt(Instant.now());
        verification.setUpdatedAt(Instant.now());
        verificationRepository.save(verification);

        String cooldownKey =
                AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + pendingAccount.getId();
        redisTemplate.delete(cooldownKey);
    }

    @Test
    @DisplayName("Should successfully resend verification code with 60s cooldown")
    void shouldSuccessfullyResendWithCooldown() throws Exception {
        ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(TEST_EMAIL).build();

        mockMvc.perform(
                        post(RESEND_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.SUCCESS))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
                .andExpect(jsonPath("$.data.message").value(AuthMessage.VERIFICATION_CODE_RESENT));

        String cooldownKey =
                AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + pendingAccount.getId();
        String cooldownValue = redisTemplate.opsForValue().get(cooldownKey);
        assertThat(cooldownValue).isNotNull();

        Long remainingTtl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        assertThat(remainingTtl).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Should return HTTP 429 when cooldown is active")
    void shouldReturn429WhenCooldownActive() throws Exception {
        String cooldownKey =
                AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + pendingAccount.getId();
        redisTemplate
                .opsForValue()
                .set(cooldownKey, "1", AuthVerificationConstants.COOLDOWN_DURATION);

        ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(TEST_EMAIL).build();

        mockMvc.perform(
                        post(RESEND_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Should invalidate previous PENDING verification codes")
    void shouldInvalidatePreviousPendingCodes() throws Exception {
        long initialPendingCount =
                verificationRepository
                        .findAllByAccountIdAndStatus(
                                pendingAccount.getId(), VerificationStatus.PENDING)
                        .size();
        assertThat(initialPendingCount).isEqualTo(1);

        ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(TEST_EMAIL).build();

        mockMvc.perform(
                        post(RESEND_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        long finalPendingCount =
                verificationRepository
                        .findAllByAccountIdAndStatus(
                                pendingAccount.getId(), VerificationStatus.PENDING)
                        .size();
        assertThat(finalPendingCount).isEqualTo(1);

        long expiredCount =
                verificationRepository
                        .findAllByAccountIdAndStatus(
                                pendingAccount.getId(), VerificationStatus.EXPIRED)
                        .size();
        assertThat(expiredCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return HTTP 409 for already active account")
    void shouldReturn409ForActiveAccount() throws Exception {
        pendingAccount.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(pendingAccount);

        ResendVerificationRequest request =
                ResendVerificationRequest.builder().email(TEST_EMAIL).build();

        mockMvc.perform(
                        post(RESEND_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_ALREADY_ACTIVE));
    }

    @Test
    @DisplayName("Should return HTTP 404 for nonexistent email")
    void shouldReturn404ForNonexistentEmail() throws Exception {
        ResendVerificationRequest request =
                ResendVerificationRequest.builder().email("nonexistent@example.com").build();

        mockMvc.perform(
                        post(RESEND_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
