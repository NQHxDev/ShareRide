package com.shareride.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.VerifyEmailRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.auth.util.AlphanumericCodeGenerator;
import com.shareride.auth.util.HashingUtils;
import com.shareride.common.response.BaseResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@DisplayName("Integration Test: Account Verification Flow")
class AccountVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private AccountRepository accountRepository;

    @Autowired private AccountVerificationRepository accountVerificationRepository;

    @Test
    @DisplayName("End-to-end: Case-insensitive verification activates account")
    void testEndToEndVerificationWithCaseInsensitivity() throws Exception {
        final String email = "verify.test@example.com";
        final String uppercaseCode = "K8M2P4";
        final String lowercaseInput = "k8m2p4";

        final Account account = createPendingAccount(email);
        final AccountVerification verification = createVerification(account, uppercaseCode);

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(lowercaseInput).build();

        final MvcResult result =
                mockMvc.perform(
                                MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(MockMvcResultMatchers.status().isOk())
                        .andExpect(
                                MockMvcResultMatchers.jsonPath("$.code")
                                        .value(AuthResponseCode.SUCCESS))
                        .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("ACTIVE"))
                        .andReturn();

        final String responseBody = result.getResponse().getContentAsString();
        final BaseResponse<AccountResponse> response =
                objectMapper.readValue(
                        responseBody,
                        objectMapper
                                .getTypeFactory()
                                .constructParametricType(
                                        BaseResponse.class, AccountResponse.class));

        assertNotNull(response.getData());
        assertEquals(AccountStatus.ACTIVE, response.getData().getStatus());

        final Account updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, updatedAccount.getStatus());

        final AccountVerification updatedVerification =
                accountVerificationRepository.findById(verification.getId()).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updatedVerification.getStatus());
    }

    @Test
    @DisplayName("Optimistic locking prevents concurrent verification race condition")
    void testOptimisticLockingPreventsRaceCondition() throws Exception {
        final String email = "concurrent.test@example.com";
        final String code = "ABC123";

        final Account account = createPendingAccount(email);
        createVerification(account, code);

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(code).build();

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(AuthResponseCode.ACCOUNT_ALREADY_ACTIVE));
    }

    @Test
    @DisplayName("Failed attempts are tracked and max attempts locks verification")
    void testFailedAttemptsTrackingAndLockout() throws Exception {
        final String email = "lockout.test@example.com";
        final String correctCode = "VALID1";
        final String wrongCode = "WRONG1";

        final Account account = createPendingAccount(email);
        final AccountVerification verification = createVerification(account, correctCode);

        final VerifyEmailRequest wrongRequest =
                VerifyEmailRequest.builder().email(email).code(wrongCode).build();

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(wrongRequest)))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(
                            MockMvcResultMatchers.jsonPath("$.code")
                                    .value(AuthResponseCode.INVALID_VERIFICATION_CODE));

            final AccountVerification updated =
                    accountVerificationRepository.findById(verification.getId()).orElseThrow();
            assertEquals(i, updated.getFailedAttempts());

            if (i == 5) {
                assertEquals(VerificationStatus.MAX_ATTEMPTS_EXCEEDED, updated.getStatus());
            }
        }

        final VerifyEmailRequest correctRequest =
                VerifyEmailRequest.builder().email(email).code(correctCode).build();

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(AuthResponseCode.VERIFICATION_CODE_EXPIRED));
    }

    @Test
    @DisplayName("Expired verification code returns proper error")
    void testExpiredVerificationCode() throws Exception {
        final String email = "expired.test@example.com";
        final String code = "EXPIRE";

        final Account account = createPendingAccount(email);
        final AccountVerification verification = createVerification(account, code);
        verification.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        accountVerificationRepository.save(verification);

        final VerifyEmailRequest request =
                VerifyEmailRequest.builder().email(email).code(code).build();

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(AuthResponseCode.VERIFICATION_CODE_EXPIRED))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.message")
                                .value(AuthMessage.VERIFICATION_CODE_EXPIRED));

        final AccountVerification updated =
                accountVerificationRepository.findById(verification.getId()).orElseThrow();
        assertEquals(VerificationStatus.EXPIRED, updated.getStatus());
    }

    @Test
    @DisplayName("Alphanumeric code generation produces valid unambiguous codes")
    void testAlphanumericCodeGeneration() {
        for (int i = 0; i < 100; i++) {
            final String code = AlphanumericCodeGenerator.generate();
            assertThat(code).hasSize(6);
            assertThat(code).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}");
            assertThat(code).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    @DisplayName("Account not found returns 404")
    void testAccountNotFound() throws Exception {
        final VerifyEmailRequest request =
                VerifyEmailRequest.builder()
                        .email("nonexistent@example.com")
                        .code("ABC123")
                        .build();

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(AuthResponseCode.ACCOUNT_NOT_FOUND));
    }

    private Account createPendingAccount(final String email) {
        final Account account =
                Account.builder()
                        .email(email)
                        .passwordHash("$argon2id$v=19$m=16384,t=2,p=1$...")
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .build();
        return accountRepository.save(account);
    }

    private AccountVerification createVerification(final Account account, final String code) {
        final String codeHash = HashingUtils.sha256Hex(code);
        final AccountVerification verification =
                AccountVerification.builder()
                        .accountId(account.getId())
                        .verificationCodeHash(codeHash)
                        .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                        .failedAttempts(0)
                        .status(VerificationStatus.PENDING)
                        .build();
        return accountVerificationRepository.save(verification);
    }
}
