package com.shareride.auth.service;

/**
 * Service interface for managing email existence checks using a Bloom filter stored in Valkey
 * database 0.
 *
 * <p>This service provides a fast probabilistic pre-check layer before expensive PostgreSQL queries
 * during user registration. The Bloom filter is isolated in Valkey database 0 to comply with the
 * Service Per Database principle.
 *
 * <p>Characteristics: - False positives possible (filter says "exists" but doesn't) - triggers DB
 * confirmation - False negatives impossible (filter says "not exists" means definitely not in
 * system) - Asynchronous email additions after PostgreSQL transaction commits - Graceful
 * degradation: falls back to PostgreSQL if Valkey unavailable
 *
 * <p>Thread-safety: All methods are thread-safe and can be called concurrently.
 */
public interface EmailBloomFilterService {

    /**
     * Checks if an email might exist in the system using the Bloom filter.
     *
     * <p>This method provides a fast probabilistic check with the following guarantees: - If
     * returns false: email definitely does NOT exist (no DB query needed) - If returns true: email
     * MIGHT exist (requires PostgreSQL confirmation due to false positives)
     *
     * <p>Performance: O(k) where k is number of hash functions (~7 for 1% error rate) Expected
     * latency: <5ms
     *
     * @param email the email address to check (must be normalized: trimmed, lowercase)
     * @return true if email might exist (requires DB confirmation), false if definitely does not
     *     exist
     * @throws IllegalArgumentException if email is null or blank
     */
    boolean mightExist(String email);

    /**
     * Adds an email to the Bloom filter.
     *
     * <p>This method should be called asynchronously AFTER the PostgreSQL transaction commits
     * successfully (via @TransactionalEventListener with AFTER_COMMIT phase).
     *
     * <p>Idempotent: Adding the same email multiple times is safe (no-op after first add).
     *
     * <p>Performance: O(k) where k is number of hash functions Expected latency: <10ms
     *
     * @param email the email address to add (must be normalized: trimmed, lowercase)
     * @throws IllegalArgumentException if email is null or blank
     */
    void addEmail(String email);

    /**
     * Initializes the Bloom filter in Valkey database 0 with configured capacity and error rate.
     *
     * <p>This method is called automatically on application startup if the filter does not exist.
     * Uses BF.RESERVE command with parameters from BloomFilterProperties: - error_rate: 0.01 (1%
     * false positive probability) - capacity: 1,000,000 emails
     *
     * <p>If filter already exists, this method is a no-op (does not reinitialize).
     */
    void initializeBloomFilter();

    /**
     * Rebuilds the Bloom filter by clearing and repopulating from PostgreSQL account data.
     *
     * <p>This operation is blocking and should only be called by administrators via a secured
     * endpoint. Use cases: - Bloom filter lost (Valkey restart without persistence) - Filter
     * corruption detected - Capacity increase required (delete old filter, create new with higher
     * capacity)
     *
     * <p>Implementation: 1. Delete existing filter (DEL auth:email:bloom) 2. Initialize new filter
     * (BF.RESERVE with configured parameters) 3. Paginated read from PostgreSQL (1000 emails per
     * batch) 4. Batch add to Bloom filter (BF.ADD for each email)
     *
     * <p>Performance: O(n) where n = total accounts in PostgreSQL Expected duration: ~60 seconds
     * for 50,000 accounts
     *
     * <p>Thread-safety: Only one rebuild operation should run at a time (caller must enforce via
     * lock/semaphore)
     *
     * @return the number of emails added to the rebuilt filter
     */
    long rebuildFromDatabase();

    /**
     * Clears the Bloom filter by deleting the key from Valkey database 0.
     *
     * <p>This method is destructive and should only be called during rebuild operations or
     * administrative maintenance. After clearing, the filter must be re-initialized via
     * initializeBloomFilter() before use.
     */
    void clear();

    /**
     * Checks if the Bloom filter is initialized and ready for use.
     *
     * <p>Returns true if the key exists in Valkey database 0, false otherwise. Useful for health
     * checks and startup validation.
     *
     * @return true if filter exists and is ready, false if uninitialized
     */
    boolean isInitialized();

    /**
     * Retrieves Bloom filter statistics for observability and monitoring.
     *
     * <p>Returns metadata about the filter state: - Number of items added (approximate) - Memory
     * usage in bytes - Number of hash functions - Expansion rate
     *
     * <p>Maps to BF.INFO command output.
     *
     * @return BloomFilterInfo containing filter statistics
     */
    BloomFilterInfo getFilterInfo();

    /** Value object containing Bloom filter statistics. */
    record BloomFilterInfo(
            long capacity,
            long size,
            long numberOfFilters,
            long numberOfItemsInserted,
            long expansionRate) {}
}
