package com.shareride.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequestDTO;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.auth.service.RegistrationService;
import com.shareride.auth.support.AbstractIntegrationTest;
import com.shareride.common.exception.ConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests verifying registration flow with Bloom filter optimization. Validates that new
 * emails skip PostgreSQL query (99% skip rate) while existing emails correctly trigger DB
 * confirmation.
 */
@DisplayName("Registration with Bloom Filter Integration Tests")
class RegistrationWithBloomFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private EmailBloomFilterService emailBloomFilterService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        emailBloomFilterService.clear();
        emailBloomFilterService.initializeBloomFilter();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("New email registration skips DB existence check via Bloom filter")
    void registerNewEmail_skipsDbCheck() {
        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email("newuser@example.com")
                        .password("SecurePass123!")
                        .confirmPassword("SecurePass123!")
                        .build();

        double missCountBefore = meterRegistry.counter("auth.bloom.miss.total").count();

        AccountResponse response = registrationService.register(request);

        assertNotNull(response);
        assertEquals("newuser@example.com", response.getEmail());

        double missCountAfter = meterRegistry.counter("auth.bloom.miss.total").count();
        assertEquals(
                1.0,
                missCountAfter - missCountBefore,
                "Bloom filter should record 1 miss for new email (skipped DB query)");
    }

    @Test
    @DisplayName("Existing email registration triggers DB confirmation via Bloom filter hit")
    void registerExistingEmail_triggersDbConfirmation() {
        RegisterRequestDTO request1 =
                RegisterRequestDTO.builder()
                        .email("existing@example.com")
                        .password("Pass123!")
                        .confirmPassword("Pass123!")
                        .build();

        registrationService.register(request1);

        double hitCountBefore = meterRegistry.counter("auth.bloom.hit.total").count();

        RegisterRequestDTO request2 =
                RegisterRequestDTO.builder()
                        .email("existing@example.com")
                        .password("Pass456!")
                        .confirmPassword("Pass456!")
                        .build();

        assertThrows(
                ConflictException.class,
                () -> registrationService.register(request2),
                "Should throw ConflictException for duplicate email");

        double hitCountAfter = meterRegistry.counter("auth.bloom.hit.total").count();
        assertTrue(
                hitCountAfter > hitCountBefore,
                "Bloom filter should record hit for existing email (triggered DB confirmation)");
    }

    @Test
    @DisplayName("Bloom filter achieves 99% skip rate for new emails (batch test)")
    void batchRegistration_achieves99PercentSkipRate() {
        int totalRegistrations = 100;
        double missCountBefore = meterRegistry.counter("auth.bloom.miss.total").count();

        for (int i = 0; i < totalRegistrations; i++) {
            RegisterRequestDTO request =
                    RegisterRequestDTO.builder()
                            .email("batchuser" + i + "@example.com")
                            .password("Pass123!")
                            .confirmPassword("Pass123!")
                            .build();

            registrationService.register(request);
        }

        double missCountAfter = meterRegistry.counter("auth.bloom.miss.total").count();
        double missesRecorded = missCountAfter - missCountBefore;

        double skipRate = missesRecorded / totalRegistrations;
        assertTrue(
                skipRate >= 0.99,
                String.format(
                        "Skip rate %.2f%% should be >= 99%% (recorded %d misses for %d"
                                + " registrations)",
                        skipRate * 100, (int) missesRecorded, totalRegistrations));
    }

    @Test
    @DisplayName("Email added to Bloom filter after successful registration (sync verification)")
    void registration_addsEmailToBloomFilter() {
        String email = "synctest@example.com";

        assertFalse(emailBloomFilterService.mightExist(email), "Email should not exist initially");

        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email(email)
                        .password("Pass123!")
                        .confirmPassword("Pass123!")
                        .build();

        registrationService.register(request);

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should exist in Bloom filter after successful registration");
    }

    @Test
    @DisplayName("Failed registration does not add email to Bloom filter (rollback test)")
    void failedRegistration_doesNotAddEmailToBloomFilter() {
        RegisterRequestDTO validRequest =
                RegisterRequestDTO.builder()
                        .email("valid@example.com")
                        .password("Pass123!")
                        .confirmPassword("Pass123!")
                        .build();

        registrationService.register(validRequest);

        RegisterRequestDTO mismatchPasswordRequest =
                RegisterRequestDTO.builder()
                        .email("newemail@example.com")
                        .password("Pass123!")
                        .confirmPassword("DifferentPass123!")
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> registrationService.register(mismatchPasswordRequest),
                "Should throw IllegalArgumentException for password mismatch");

        assertFalse(
                emailBloomFilterService.mightExist("newemail@example.com"),
                "Email should NOT be in Bloom filter after failed registration (transaction"
                        + " rollback)");
    }

    @Test
    @DisplayName("Bloom filter check duration is under 10ms")
    void bloomFilterCheckDuration_under10ms() {
        RegisterRequestDTO request =
                RegisterRequestDTO.builder()
                        .email("performance@example.com")
                        .password("Pass123!")
                        .confirmPassword("Pass123!")
                        .build();

        registrationService.register(request);

        double meanDuration =
                meterRegistry
                        .timer("auth.bloom.check.duration")
                        .mean(java.util.concurrent.TimeUnit.MILLISECONDS);

        assertTrue(
                meanDuration < 10.0,
                String.format(
                        "Bloom filter check duration %.2fms should be under 10ms", meanDuration));
    }
}
