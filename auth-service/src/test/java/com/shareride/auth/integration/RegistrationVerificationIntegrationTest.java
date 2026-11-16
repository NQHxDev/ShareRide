package com.shareride.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.repository.AccountVerificationRepository;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.common.outbox.OutboxMessageRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for User Story 1: Instant Registration with Asynchronous Verification Email.
 *
 * <p>Verifies end-to-end registration flow using full Spring Boot context with Testcontainers
 * (PostgreSQL 17 + Valkey). Tests assert:
 *
 * <ul>
 *   <li>Account is persisted in PENDING_VERIFICATION status
 *   <li>Verification challenge is created with SHA-256 hash and 15-minute expiry
 *   <li>Outbox table records ACCOUNT_VERIFICATION event for async dispatch
 *   <li>HTTP response latency meets SC-001 requirement (&lt; 1.5s)
 *   <li>Duplicate pending email registration is rejected with HTTP 409
 * </ul>
 */
@AutoConfigureMockMvc
@DisplayName("Registration Verification Integration Tests")
class RegistrationVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private AccountRepository accountRepository;

    @Autowired private AccountVerificationRepository accountVerificationRepository;

    @Autowired private OutboxMessageRepository outboxMessageRepository;

    @BeforeEach
    void setUp() {
        accountVerificationRepository.deleteAll();
        accountRepository.deleteAll();
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName(
            "POST /auth/v1/register creates account in PENDING_VERIFICATION and returns < 1.5s"
                    + " (SC-001)")
    void register_createsAccountInPendingVerification_returnsWithinLatencyBudget()
            throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("newuser@example.com")
                        .password("SecurePass123!")
                        .phoneNumber("+84901234567")
                        .fullName("Nguyen Van A")
                        .roleCode("RIDER")
                        .build();

        long startTime = System.currentTimeMillis();

        mockMvc.perform(
                        post("/auth/v1/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));

        long elapsedTime = System.currentTimeMillis() - startTime;

        assertThat(elapsedTime)
                .as("Registration HTTP response latency must be < 1.5s (SC-001)")
                .isLessThan(1500L);

        // Verify account persisted in database with PENDING_VERIFICATION status
        var account = accountRepository.findByEmail("newuser@example.com");
        assertThat(account).isPresent();
        assertThat(account.get().getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    @DisplayName(
            "POST /auth/v1/register persists AccountVerification with SHA-256 hash and 15-minute"
                    + " expiry")
    void register_persistsVerificationChallenge_withHashAndExpiry() throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("verification@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84902345678")
                        .roleCode("RIDER")
                        .build();

        mockMvc.perform(
                        post("/auth/v1/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify verification challenge persisted
        var account = accountRepository.findByEmail("verification@example.com");
        assertThat(account).isPresent();

        var verification =
                accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.get().getId(), VerificationStatus.PENDING);

        assertThat(verification).isPresent();

        AccountVerification challenge = verification.get();
        assertThat(challenge.getVerificationCodeHash())
                .as("SHA-256 hash must be 64-character hex string")
                .hasSize(64)
                .matches("^[a-f0-9]{64}$");

        assertThat(challenge.getExpiresAt())
                .as("Verification code must expire in approximately 15 minutes")
                .isAfter(Instant.now().plusSeconds(14 * 60))
                .isBefore(Instant.now().plusSeconds(16 * 60));

        assertThat(challenge.getFailedAttempts()).isZero();
        assertThat(challenge.getStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    @DisplayName(
            "POST /auth/v1/register records ACCOUNT_VERIFICATION event in outbox table for async"
                    + " dispatch")
    void register_recordsOutboxEvent_forAsyncEmailDispatch() throws Exception {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("outbox@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84903456789")
                        .fullName("Test User")
                        .roleCode("RIDER")
                        .build();

        mockMvc.perform(
                        post("/auth/v1/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify outbox message recorded
        var outboxMessages = outboxMessageRepository.findAll();
        assertThat(outboxMessages)
                .as("Outbox should contain ACCOUNT_VERIFICATION event")
                .isNotEmpty()
                .anySatisfy(
                        message -> {
                            assertThat(message.getEventType()).isEqualTo("ACCOUNT_VERIFICATION");
                            assertThat(message.getAggregateType()).isEqualTo("Account");
                        });
    }

    @Test
    @DisplayName(
            "POST /auth/v1/register with duplicate PENDING_VERIFICATION email returns HTTP 409"
                    + " EMAIL_PENDING_VERIFICATION")
    void register_withDuplicatePendingEmail_returns409Conflict() throws Exception {
        RegisterRequest firstRequest =
                RegisterRequest.builder()
                        .email("duplicate@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84904567890")
                        .roleCode("RIDER")
                        .build();

        // First registration succeeds
        mockMvc.perform(
                        post("/auth/v1/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));

        RegisterRequest duplicateRequest =
                RegisterRequest.builder()
                        .email("duplicate@example.com")
                        .password("DifferentPass456!")
                        .phoneNumber("+84905678901")
                        .roleCode("DRIVER")
                        .build();

        // Second registration with same email should fail with HTTP 409
        mockMvc.perform(
                        post("/auth/v1/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_PENDING_VERIFICATION"));

        // Verify only one account exists in database
        var accounts = accountRepository.findAll();
        assertThat(accounts)
                .as("Only the first account should be persisted")
                .hasSize(1)
                .first()
                .satisfies(
                        account -> {
                            assertThat(account.getEmail()).isEqualTo("duplicate@example.com");
                            assertThat(account.getStatus())
                                    .isEqualTo(AccountStatus.PENDING_VERIFICATION);
                        });
    }

    @Test
    @DisplayName(
            "POST /auth/v1/register generates unique verification code hashes for concurrent"
                    + " registrations")
    void register_generatesDifferentCodeHashes_forConcurrentRegistrations() throws Exception {
        RegisterRequest request1 =
                RegisterRequest.builder()
                        .email("user1@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84906789012")
                        .roleCode("RIDER")
                        .build();

        RegisterRequest request2 =
                RegisterRequest.builder()
                        .email("user2@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84907890123")
                        .roleCode("RIDER")
                        .build();

        mockMvc.perform(
                post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)));

        mockMvc.perform(
                post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)));

        var account1 = accountRepository.findByEmail("user1@example.com");
        var account2 = accountRepository.findByEmail("user2@example.com");

        assertThat(account1).isPresent();
        assertThat(account2).isPresent();

        var verification1 =
                accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account1.get().getId(), VerificationStatus.PENDING);

        var verification2 =
                accountVerificationRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account2.get().getId(), VerificationStatus.PENDING);

        assertThat(verification1).isPresent();
        assertThat(verification2).isPresent();

        assertThat(verification1.get().getVerificationCodeHash())
                .as("Each registration must generate a unique verification code hash")
                .isNotEqualTo(verification2.get().getVerificationCodeHash());
    }
}
