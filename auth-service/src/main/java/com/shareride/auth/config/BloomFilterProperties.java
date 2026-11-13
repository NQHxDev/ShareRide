package com.shareride.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for email Bloom filter optimization. Maps to 'app.bloom-filter' section
 * in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.bloom-filter")
public class BloomFilterProperties {

    /** Valkey key name for the Bloom filter. Default: auth:email:bloom */
    private String key = "auth:email:bloom";

    /**
     * False positive error rate (0.0 to 1.0). Lower values = more memory usage. Default: 0.01 (1%)
     */
    private double errorRate = 0.01;

    /** Expected number of items (emails). Default: 1,000,000 */
    private long initialCapacity = 1_000_000L;

    /**
     * Enable/disable Bloom filter optimization. If false, all checks go directly to PostgreSQL.
     * Default: true
     */
    private boolean enabled = true;
}
