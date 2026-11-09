package com.shareride.common.cache;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties supporting the Hybrid Cache Architecture (Valkey): - Shared Cache:
 * Cluster/Instance shared across microservices (distributed locks, blacklist, rate limit) - Local
 * Cache: Dedicated instance for microservice-specific high-throughput caching (e.g. Geolocation in
 * ride-service)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.cache")
public class ShareRideCacheProperties {

    /** Master switch for Cache configuration in this microservice. */
    private boolean enabled = true;

    /**
     * Shared Cache configuration (Distributed Locks, Token Blacklist, Cross-service Coordination).
     */
    private CacheInstanceProperties shared = new CacheInstanceProperties(0);

    /** Local Dedicated Cache configuration (Service-specific internal caching). */
    private LocalCacheInstanceProperties local = new LocalCacheInstanceProperties(1);

    @Getter
    @Setter
    public static class CacheInstanceProperties {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database;
        private Duration timeout = Duration.ofSeconds(2);
        private boolean ssl = false;

        public CacheInstanceProperties() {
            this.database = 0;
        }

        public CacheInstanceProperties(int defaultDb) {
            this.database = defaultDb;
        }
    }

    @Getter
    @Setter
    public static class LocalCacheInstanceProperties extends CacheInstanceProperties {
        /** Whether this microservice has its own dedicated local Cache instance. */
        private boolean enabled = false;

        public LocalCacheInstanceProperties() {
            super(1);
        }

        public LocalCacheInstanceProperties(int defaultDb) {
            super(defaultDb);
        }
    }
}
