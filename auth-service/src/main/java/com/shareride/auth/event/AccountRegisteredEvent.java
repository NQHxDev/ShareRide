package com.shareride.auth.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Domain event published after a new account is successfully created and persisted. This event
 * triggers asynchronous Bloom filter synchronization.
 *
 * <p>Lifecycle: 1. Account saved to PostgreSQL 2. Event published (within transaction) 3.
 * Transaction commits 4. Listeners receive event (AFTER_COMMIT phase) 5. Email added to Bloom
 * filter asynchronously
 */
@Getter
@AllArgsConstructor
public class AccountRegisteredEvent {
    private final String email;
}
