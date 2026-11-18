package com.shareride.auth.endpoint;

import com.shareride.auth.service.EmailBloomFilterService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator Management Endpoint for Bloom Filter administrative operations. Exposes
 * filter rebuild operations on the Actuator management port (8081) per Phase 8 User Story 6 (T073).
 *
 * <p>Replaces the legacy AdminBloomFilterController REST endpoint with an Actuator-based management
 * interface, removing business logic from public HTTP controllers.
 *
 * <p>Accessible at: POST http://localhost:8081/actuator/bloom-filter/rebuild
 */
@Slf4j
@Component
@Endpoint(id = "bloom-filter")
@RequiredArgsConstructor
public class BloomFilterEndpoint {

    private final EmailBloomFilterService emailBloomFilterService;

    /**
     * Rebuild the email Bloom filter from the database. This is an administrative operation that
     * reloads all registered emails into the Bloom filter stored in Valkey.
     *
     * <p>Use cases: - Manual filter rebuild after data corruption - Periodic maintenance to refresh
     * the filter - Recovery after cache eviction or Redis restart
     *
     * @return Map containing rebuild status and statistics
     */
    @WriteOperation
    public Map<String, Object> rebuild() {
        log.info("Bloom filter rebuild initiated via Actuator endpoint");

        try {
            long startTime = System.currentTimeMillis();
            long emailsAdded = emailBloomFilterService.rebuildFromDatabase();
            long duration = System.currentTimeMillis() - startTime;

            log.info(
                    "Bloom filter rebuild completed successfully in {}ms, {} emails added",
                    duration,
                    emailsAdded);

            return Map.of(
                    "success",
                    true,
                    "message",
                    "Bloom filter rebuilt successfully",
                    "emailsAdded",
                    emailsAdded,
                    "durationMs",
                    duration,
                    "timestamp",
                    Instant.now().toString());
        } catch (Exception e) {
            log.error("Bloom filter rebuild failed", e);
            return Map.of(
                    "success",
                    false,
                    "message",
                    "Bloom filter rebuild failed: " + e.getMessage(),
                    "error",
                    e.getClass().getSimpleName(),
                    "timestamp",
                    Instant.now().toString());
        }
    }

    /**
     * Get Bloom filter statistics and status.
     *
     * @return Map containing filter statistics
     */
    @ReadOperation
    public Map<String, Object> getStats() {
        log.debug("Bloom filter stats requested via Actuator endpoint");

        try {
            EmailBloomFilterService.BloomFilterInfo info = emailBloomFilterService.getFilterInfo();
            return Map.of(
                    "success",
                    true,
                    "initialized",
                    emailBloomFilterService.isInitialized(),
                    "capacity",
                    info.capacity(),
                    "size",
                    info.size(),
                    "itemsInserted",
                    info.numberOfItemsInserted(),
                    "timestamp",
                    Instant.now().toString());
        } catch (Exception e) {
            log.error("Failed to retrieve Bloom filter stats", e);
            return Map.of(
                    "success",
                    false,
                    "message",
                    "Failed to retrieve stats: " + e.getMessage(),
                    "timestamp",
                    Instant.now().toString());
        }
    }
}
