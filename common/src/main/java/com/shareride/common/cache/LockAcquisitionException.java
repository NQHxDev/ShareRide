package com.shareride.common.cache;

/**
 * Exception thrown when a distributed lock cannot be acquired within the configured wait time or
 * retry limits.
 */
public class LockAcquisitionException extends IllegalStateException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
