package com.shareride.auth.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.RegisterRequest;
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
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("newuser@example.com")
                        .password("SecurePass123!")
                        .phoneNumber("+84901234567")
                        .roleCode("RIDER")
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
        RegisterRequest request1 =
                RegisterRequest.builder()
                        .email("existing@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84901111111")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(request1);

        double hitCountBefore = meterRegistry.counter("auth.bloom.hit.total").count();

        RegisterRequest request2 =
                RegisterRequest.builder()
                        .email("existing@example.com")
                        .password("Pass456!")
                        .phoneNumber("+84902222222")
                        .roleCode("RIDER")
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
            RegisterRequest request =
                    RegisterRequest.builder()
                            .email("batchuser" + i + "@example.com")
                            .password("Pass123!")
                            .phoneNumber("+8490" + String.format("%07d", i))
                            .roleCode("RIDER")
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

        RegisterRequest request =
                RegisterRequest.builder()
                        .email(email)
                        .password("Pass123!")
                        .phoneNumber("+84903333333")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(request);

        assertTrue(
                emailBloomFilterService.mightExist(email),
                "Email should exist in Bloom filter after successful registration");
    }

    @Test
    @DisplayName("Failed registration does not add email to Bloom filter (rollback test)")
    void failedRegistration_doesNotAddEmailToBloomFilter() {
        RegisterRequest validRequest =
                RegisterRequest.builder()
                        .email("valid@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84904444444")
                        .roleCode("RIDER")
                        .build();

        registrationService.register(validRequest);

        RegisterRequest duplicatePhoneRequest =
                RegisterRequest.builder()
                        .email("newemail@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84904444444")
                        .roleCode("RIDER")
                        .build();

        assertThrows(
                ConflictException.class,
                () -> registrationService.register(duplicatePhoneRequest),
                "Should throw ConflictException for duplicate phone");

        assertFalse(
                emailBloomFilterService.mightExist("newemail@example.com"),
                "Email should NOT be in Bloom filter after failed registration (transaction"
                        + " rollback)");
    }

    @Test
    @DisplayName("Bloom filter check duration is under 10ms")
    void bloomFilterCheckDuration_under10ms() {
        RegisterRequest request =
                RegisterRequest.builder()
                        .email("performance@example.com")
                        .password("Pass123!")
                        .phoneNumber("+84905555555")
                        .roleCode("RIDER")
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
