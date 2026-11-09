package com.shareride.common.outbox;

/**
 * Strategy interface for handling outbox messages that have permanently failed after exceeding the
 * maximum allowed retry attempts.
 */
@FunctionalInterface
public interface OutboxDeadLetterHandler {

    /**
     * Handles a permanently failed message (e.g., logging an alert, forwarding to a DLQ topic).
     *
     * @param message the dead-lettered outbox message
     */
    void handle(OutboxMessage message);
}
