package com.shareride.auth.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.auth.service.EmailBloomFilterService.BloomFilterInfo;
import com.shareride.auth.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for EmailBloomFilterService with real Valkey Testcontainer. Validates full
 * Bloom filter workflow: initialize, add, check existence.
 */
@DisplayName("EmailBloomFilter Integration Tests")
class EmailBloomFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EmailBloomFilterService emailBloomFilterService;

    @BeforeEach
    void setUp() {
        emailBloomFilterService.clear();
        emailBloomFilterService.initializeBloomFilter();
    }

    @Test
    @DisplayName("Full flow: initialize filter, add email, check existence")
    void fullFlow_initializeAddCheck() {
        String email = "integrationtest@example.com";

        assertFalse(
                emailBloomFilterService.mightExist(email),
                "New email should not exist in fresh Bloom filter");

        emailBloomFilterService.addEmail(email);

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should exist after being added to Bloom filter");
    }

    @Test
    @DisplayName("Bloom filter detects multiple emails independently")
    void multipleEmails_independentDetection() {
        String email1 = "user1@example.com";
        String email2 = "user2@example.com";
        String email3 = "user3@example.com";

        emailBloomFilterService.addEmail(email1);
        emailBloomFilterService.addEmail(email2);

        assertTrue(emailBloomFilterService.mightExist(email1), "Email 1 should exist");
        assertTrue(emailBloomFilterService.mightExist(email2), "Email 2 should exist");
        assertFalse(emailBloomFilterService.mightExist(email3), "Email 3 should not exist");
    }

    @Test
    @DisplayName("Bloom filter is idempotent (adding same email multiple times)")
    void addEmail_idempotent() {
        String email = "duplicate@example.com";

        emailBloomFilterService.addEmail(email);
        emailBloomFilterService.addEmail(email);
        emailBloomFilterService.addEmail(email);

        assertTrue(
                emailBloomFilterService.mightExist(email), "Email should exist after duplicates");
    }

    @Test
    @DisplayName("Email normalization works (case insensitive, trimmed)")
    void emailNormalization_caseInsensitiveTrimmed() {
        emailBloomFilterService.addEmail("  TestUser@Example.COM  ");

        assertTrue(
                emailBloomFilterService.mightExist("testuser@example.com"),
                "Normalized email should be found");
        assertTrue(
                emailBloomFilterService.mightExist("TESTUSER@EXAMPLE.COM"),
                "Uppercase email should be normalized and found");
        assertTrue(
                emailBloomFilterService.mightExist("  testuser@example.com  "),
                "Email with spaces should be trimmed and found");
    }

    @Test
    @DisplayName("isInitialized returns true after initialization")
    void isInitialized_afterInit() {
        assertTrue(emailBloomFilterService.isInitialized(), "Bloom filter should be initialized");
    }

    @Test
    @DisplayName("isInitialized returns false after clear")
    void isInitialized_afterClear() {
        emailBloomFilterService.clear();

        assertFalse(
                emailBloomFilterService.isInitialized(), "Bloom filter should not be initialized");
    }

    @Test
    @DisplayName("getFilterInfo returns metadata with correct capacity")
    void getFilterInfo_returnsMetadata() {
        BloomFilterInfo info = emailBloomFilterService.getFilterInfo();

        assertNotNull(info, "Filter info should not be null");
        assertEquals(1000000L, info.capacity(), "Capacity should match configured value");
        assertTrue(info.size() > 0, "Size should be greater than 0");
        assertEquals(1L, info.numberOfFilters(), "Should have 1 filter");
    }

    @Test
    @DisplayName("Bloom filter handles 1000 emails without false negatives")
    void bloomFilter_handles1000Emails() {
        for (int i = 0; i < 1000; i++) {
            String email = "user" + i + "@example.com";
            emailBloomFilterService.addEmail(email);
        }

        for (int i = 0; i < 1000; i++) {
            String email = "user" + i + "@example.com";
            assertTrue(
                    emailBloomFilterService.mightExist(email),
                    "Email " + email + " should exist (no false negatives allowed)");
        }
    }

    @Test
    @DisplayName("False positive rate stays below 1% for 10000 emails")
    void bloomFilter_falsePositiveRateBelow1Percent() {
        for (int i = 0; i < 10000; i++) {
            emailBloomFilterService.addEmail("existing" + i + "@example.com");
        }

        int falsePositives = 0;
        int checksPerformed = 10000;

        for (int i = 0; i < checksPerformed; i++) {
            String nonExistentEmail = "nonexistent" + i + "@example.com";
            if (emailBloomFilterService.mightExist(nonExistentEmail)) {
                falsePositives++;
            }
        }

        double falsePositiveRate = (double) falsePositives / checksPerformed;
        assertTrue(
                falsePositiveRate < 0.01,
                String.format(
                        "False positive rate %.4f%% should be below 1%% (found %d false positives"
                                + " in %d checks)",
                        falsePositiveRate * 100, falsePositives, checksPerformed));
    }
}
