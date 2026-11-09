package com.shareride.common.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Transactional Outbox pattern, batch relay mechanism, retry
 * strategies, and dead letter queue behavior.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.outbox")
public class OutboxProperties {

    /** Whether the outbox module is enabled globally. Defaults to true. */
    private boolean enabled = true;

    /**
     * Number of outbox messages to fetch and process in each relay batch iteration. Defaults to 50.
     */
    private int batchSize = 50;

    /** Maximum retry attempts before marking a message as DEAD_LETTER. Defaults to 3. */
    private int maxRetries = 3;

    /**
     * Base backoff delay in milliseconds between retries for failed messages. Defaults to 1000ms.
     */
    private long retryBackoffMs = 1000L;

    /** Multiplier for exponential backoff on subsequent retries. Defaults to 2.0. */
    private double backoffMultiplier = 2.0;

    /** Maximum cap on backoff delay in milliseconds. Defaults to 60000ms (1 minute). */
    private long maxBackoffMs = 60000L;

    /** Background relay scheduler settings. */
    private Relay relay = new Relay();

    @Getter
    @Setter
    public static class Relay {

        /** Whether the background polling scheduler is active. Defaults to true. */
        private boolean enabled = true;

        /**
         * Fixed delay in milliseconds between the completion of one relay run and the start of the
         * next.
         */
        private long fixedDelayMs = 2000L;

        /**
         * Initial delay in milliseconds before the first relay execution commences. Defaults to
         * 5000ms.
         */
        private long initialDelayMs = 5000L;
    }
}
