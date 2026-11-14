package com.shareride.auth.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests verifying email sync to Bloom filter after account creation, and rollback
 * scenarios ensure email NOT added on transaction failure.
 */
@DisplayName("Account Creation Bloom Filter Sync Integration Tests")
class AccountCreationSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private EmailBloomFilterService emailBloomFilterService;
    @Autowired private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        emailBloomFilterService.clear();
        emailBloomFilterService.initializeBloomFilter();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("Email added to Bloom filter after successful account creation")
    void successfulAccountCreation_addsEmailToBloomFilter() {
        String email = "success@example.com";

        assertFalse(
                emailBloomFilterService.mightExist(email),
                "Email should not exist before registration");

        RegisterRequest request =
                RegisterRequest.builder()
                        .email(email)
                        .password("SecurePass123!")
                        .phoneNumber("+84901234567")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(request);

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should exist in Bloom filter after successful registration");
    }

    @Test
    @DisplayName("Email verified in Bloom filter via BF.EXISTS after account creation")
    void accountCreation_emailVerifiedViaBfExists() {
        String email = "verified@example.com";

        RegisterRequest request =
                RegisterRequest.builder()
                        .email(email)
                        .password("Pass123!")
                        .phoneNumber("+84902222222")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(request);

        boolean exists = emailBloomFilterService.mightExist(email);
        assertTrue(exists, "BF.EXISTS should return true for registered email");

        boolean existsAgain = emailBloomFilterService.mightExist(email);
        assertTrue(existsAgain, "BF.EXISTS should consistently return true");
    }

    @Test
    @DisplayName("Transaction rollback prevents email from being added to Bloom filter")
    void transactionRollback_doesNotAddEmailToBloomFilter() {
        RegisterRequest validRequest =
                RegisterRequest.builder()
                        .email("existing@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84903333333")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(validRequest);

        RegisterRequest duplicatePhoneRequest =
                RegisterRequest.builder()
                        .email("newemail@example.com")
                        .password("Pass456!")
                        .phoneNumber("+84903333333")
                        .roleCode("RIDER")
                        .build();

        assertThrows(
                ConflictException.class,
                () -> registrationService.register(duplicatePhoneRequest),
                "Should throw ConflictException for duplicate phone number");

        assertFalse(
                emailBloomFilterService.mightExist("newemail@example.com"),
                "Email should NOT be in Bloom filter after transaction rollback");
    }

    @Test
    @DisplayName("Duplicate email validation prevents adding to Bloom filter")
    void duplicateEmailValidation_doesNotAddToBloomFilter() {
        String email = "duplicate@example.com";

        RegisterRequest firstRequest =
                RegisterRequest.builder()
                        .email(email)
                        .password("Pass123!")
                        .phoneNumber("+84904444444")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(firstRequest);

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should exist after first registration");

        RegisterRequest secondRequest =
                RegisterRequest.builder()
                        .email(email)
                        .password("Pass456!")
                        .phoneNumber("+84905555555")
                        .roleCode("RIDER")
                        .build();

        assertThrows(
                ConflictException.class,
                () -> registrationService.register(secondRequest),
                "Should throw ConflictException for duplicate email");

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should still exist in Bloom filter (first registration succeeded)");
    }

    @Test
    @DisplayName("Multiple successful registrations sync all emails to Bloom filter")
    void multipleSuccessfulRegistrations_syncAllEmails() {
        for (int i = 0; i < 10; i++) {
            RegisterRequest request =
                    RegisterRequest.builder()
                            .email("user" + i + "@example.com")
                            .password("Pass123!")
                            .phoneNumber("+8490" + String.format("%07d", i))
                            .roleCode("RIDER")
                            .build();

            registrationService.register(request);
        }

        for (int i = 0; i < 10; i++) {
            String email = "user" + i + "@example.com";
            assertTrue(
                    emailBloomFilterService.mightExist(email),
                    "Email " + email + " should exist in Bloom filter");
        }
    }

    @Test
    @DisplayName("Bloom filter sync is idempotent (duplicate event handling)")
    void bloomFilterSync_idempotent() {
        String email = "idempotent@example.com";

        RegisterRequest request =
                RegisterRequest.builder()
                        .email(email)
                        .password("Pass123!")
                        .phoneNumber("+84906666666")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(request);

        assertTrue(
                emailBloomFilterService.mightExist(email), "Email should exist after registration");

        emailBloomFilterService.addEmail(email);
        emailBloomFilterService.addEmail(email);

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should still exist after duplicate additions (idempotent)");
    }
}
