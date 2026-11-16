package com.shareride.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.VerifyEmailRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.auth.util.AlphanumericCodeGenerator;
import com.shareride.auth.util.HashingUtils;
import com.shareride.auth.util.PasswordUtils;
import com.shareride.common.util.UuidUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@DisplayName("Integration Test: Unverified Account Login Protection")
class UnverifiedLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private AccountRepository accountRepository;

    @Autowired private AccountVerificationRepository verificationRepository;

    @Autowired private StringRedisTemplate redisTemplate;

    private Account pendingAccount;
    private String verificationCode;
    private static final String TEST_EMAIL = "unverified@integration.test";
    private static final String TEST_PASSWORD = "SecurePassword123!";
    private static final String LOGIN_URL = "/api/auth/v1/login";
    private static final String VERIFY_URL = "/api/auth/v1/verify-email";

    @BeforeEach
    void setUp() {
        verificationCode = AlphanumericCodeGenerator.generate();
        String codeHash = HashingUtils.sha256Hex(verificationCode);

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

        AccountVerification verification = new AccountVerification();
        verification.setId(UuidUtils.v7());
        verification.setAccountId(pendingAccount.getId());
        verification.setVerificationCodeHash(codeHash);
        verification.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        verification.setFailedAttempts(0);
        verification.setStatus(VerificationStatus.PENDING);
        verification.setCreatedAt(Instant.now());
        verification.setUpdatedAt(Instant.now());
        verificationRepository.save(verification);
    }

    @Test
    @DisplayName(
            "Should return HTTP 403 Forbidden when unverified account attempts login with valid"
                    + " credentials")
    void shouldReturn403_whenUnverifiedAccountAttemptsLogin() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("User-Agent", "Integration-Test/1.0")
                                .header("X-Forwarded-For", "192.168.100.50"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthResponseCode.ACCOUNT_PENDING_VERIFICATION))
                .andExpect(jsonPath("$.message").value(AuthMessage.ACCOUNT_PENDING_VERIFICATION))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.remainingCooldownSeconds").exists());
    }

    @Test
    @DisplayName("Should return cooldown metadata when resend cooldown is active")
    void shouldReturnCooldownMetadata_whenResendCooldownIsActive() throws Exception {
        String cooldownKey =
                AuthConstants.CacheKey.VERIFICATION_COOLDOWN_PREFIX + pendingAccount.getId();
        redisTemplate
                .opsForValue()
                .set(cooldownKey, "1", AuthConstants.CacheTtl.ROLE_PERMISSION_VALIDITY);
        redisTemplate.expire(cooldownKey, java.time.Duration.ofSeconds(45));

        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.remainingCooldownSeconds").exists());
    }

    @Test
    @DisplayName("Should successfully login after account verification")
    void shouldSuccessfullyLogin_afterAccountVerification() throws Exception {
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail(TEST_EMAIL);
        verifyRequest.setCode(verificationCode);

        mockMvc.perform(
                        post(VERIFY_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk());

        Account updatedAccount = accountRepository.findById(pendingAccount.getId()).orElseThrow();
        assertThat(updatedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(TEST_EMAIL);
        loginRequest.setPassword(TEST_PASSWORD);

        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest))
                                .header("User-Agent", "Integration-Test/1.0")
                                .header("X-Forwarded-For", "192.168.100.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @DisplayName("Should return HTTP 401 when invalid password is provided for pending account")
    void shouldReturn401_whenInvalidPasswordForPendingAccount() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("WrongPassword123!");

        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("User-Agent", "Integration-Test/1.0")
                                .header("X-Forwarded-For", "192.168.100.50"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(AuthMessage.INVALID_EMAIL_OR_PASSWORD));
    }
}
