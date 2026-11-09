package com.shareride.common.redis;

import java.time.Duration;

/**
 * Handle representing an acquired or attempted distributed lock. Supports auto-renewal lifecycle
 * management and clean release via {@link AutoCloseable}.
 */
public interface LockHandle extends AutoCloseable {

    /** Returns the resource key of the lock. */
    String getLockKey();

    /** Returns the unique token/UUID owning the lock. */
    String getLockToken();

    /** Returns true if the lock was successfully acquired. */
    boolean isAcquired();

    /** Returns true if the lock is a fair FIFO lock. */
    boolean isFair();

    /** Returns the configured lease duration. */
    Duration getLeaseTime();

    /**
     * Manually extends/renews the lock TTL.
     *
     * @param extension Time to extend
     * @return true if successfully extended, false otherwise
     */
    boolean renew(Duration extension);

    /** Stops auto-renewal if active and releases the lock. */
    @Override
    void close();
}
