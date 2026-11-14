package com.shareride.auth.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.auth.domain.Account;
import com.shareride.auth.domain.AccountStatus;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.auth.util.PasswordUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for Bloom filter rebuild operation. Validates rebuilding from PostgreSQL data
 * completes within performance requirements.
 */
@DisplayName("Bloom Filter Rebuild Integration Tests")
class BloomFilterRebuildIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EmailBloomFilterService emailBloomFilterService;
    @Autowired private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        emailBloomFilterService.clear();
        emailBloomFilterService.initializeBloomFilter();
    }

    @Test
    @DisplayName("Rebuild from 100 seeded accounts verifies all emails in filter")
    void rebuild_100Accounts_allEmailsPresent() {
        for (int i = 0; i < 100; i++) {
            Account account =
                    Account.builder()
                            .email("rebuild" + i + "@example.com")
                            .phoneNumber("+8490" + String.format("%07d", i))
                            .passwordHash(PasswordUtils.hashPassword("Pass123!"))
                            .status(AccountStatus.ACTIVE)
                            .roles(Set.of())
                            .build();
            accountRepository.save(account);
        }

        emailBloomFilterService.clear();
        assertFalse(
                emailBloomFilterService.isInitialized(),
                "Filter should not be initialized after clear");

        long count = emailBloomFilterService.rebuildFromDatabase();

        assertEquals(100L, count, "Should rebuild 100 emails");

        for (int i = 0; i < 100; i++) {
            String email = "rebuild" + i + "@example.com";
            assertTrue(
                    emailBloomFilterService.mightExist(email),
                    "Email " + email + " should exist in rebuilt filter");
        }
    }

    @Test
    @DisplayName("Rebuild completes within 60 seconds for large dataset")
    void rebuild_performanceTest() {
        int accountCount = 1000;

        for (int i = 0; i < accountCount; i++) {
            Account account =
                    Account.builder()
                            .email("perf" + i + "@example.com")
                            .phoneNumber("+8491" + String.format("%07d", i))
                            .passwordHash(PasswordUtils.hashPassword("Pass123!"))
                            .status(AccountStatus.ACTIVE)
                            .roles(Set.of())
                            .build();
            accountRepository.save(account);
        }

        emailBloomFilterService.clear();

        Instant start = Instant.now();
        long count = emailBloomFilterService.rebuildFromDatabase();
        Instant end = Instant.now();

        Duration duration = Duration.between(start, end);

        assertEquals(accountCount, count, "Should rebuild all accounts");
        assertTrue(
                duration.toSeconds() < 60,
                String.format(
                        "Rebuild should complete within 60 seconds (took %d seconds for %d"
                                + " accounts)",
                        duration.toSeconds(), accountCount));
    }

    @Test
    @DisplayName("Rebuild on empty database completes successfully")
    void rebuild_emptyDatabase() {
        emailBloomFilterService.clear();

        long count = emailBloomFilterService.rebuildFromDatabase();

        assertEquals(0L, count, "Should rebuild 0 emails from empty database");
        assertTrue(
                emailBloomFilterService.isInitialized(),
                "Filter should be initialized after rebuild");
    }

    @Test
    @DisplayName("Rebuild replaces existing filter data")
    void rebuild_replacesExistingData() {
        emailBloomFilterService.addEmail("old@example.com");
        assertTrue(emailBloomFilterService.mightExist("old@example.com"), "Old email should exist");

        Account account =
                Account.builder()
                        .email("new@example.com")
                        .phoneNumber("+84922222222")
                        .passwordHash(PasswordUtils.hashPassword("Pass123!"))
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of())
                        .build();
        accountRepository.save(account);

        long count = emailBloomFilterService.rebuildFromDatabase();

        assertEquals(1L, count);
        assertFalse(
                emailBloomFilterService.mightExist("old@example.com"),
                "Old email should NOT exist after rebuild");
        assertTrue(
                emailBloomFilterService.mightExist("new@example.com"),
                "New email from DB should exist after rebuild");
    }

    @Test
    @DisplayName("Rebuild handles special characters in emails")
    void rebuild_specialCharactersInEmails() {
        Account account1 =
                Account.builder()
                        .email("test+filter@example.com")
                        .phoneNumber("+84933333333")
                        .passwordHash(PasswordUtils.hashPassword("Pass123!"))
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of())
                        .build();

        Account account2 =
                Account.builder()
                        .email("user.name@example.co.uk")
                        .phoneNumber("+84944444444")
                        .passwordHash(PasswordUtils.hashPassword("Pass123!"))
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of())
                        .build();

        accountRepository.save(account1);
        accountRepository.save(account2);

        emailBloomFilterService.clear();
        long count = emailBloomFilterService.rebuildFromDatabase();

        assertEquals(2L, count);
        assertTrue(emailBloomFilterService.mightExist("test+filter@example.com"));
        assertTrue(emailBloomFilterService.mightExist("user.name@example.co.uk"));
    }

    @Test
    @DisplayName("Rebuild is idempotent (multiple rebuilds produce same result)")
    void rebuild_idempotent() {
        for (int i = 0; i < 10; i++) {
            Account account =
                    Account.builder()
                            .email("idempotent" + i + "@example.com")
                            .phoneNumber("+8495" + String.format("%07d", i))
                            .passwordHash(PasswordUtils.hashPassword("Pass123!"))
                            .status(AccountStatus.ACTIVE)
                            .roles(Set.of())
                            .build();
            accountRepository.save(account);
        }

        long count1 = emailBloomFilterService.rebuildFromDatabase();
        long count2 = emailBloomFilterService.rebuildFromDatabase();
        long count3 = emailBloomFilterService.rebuildFromDatabase();

        assertEquals(10L, count1);
        assertEquals(10L, count2);
        assertEquals(10L, count3);

        for (int i = 0; i < 10; i++) {
            assertTrue(emailBloomFilterService.mightExist("idempotent" + i + "@example.com"));
        }
    }
}
