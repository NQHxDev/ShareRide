package com.shareride.common.redis;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties supporting the Hybrid Redis Architecture: - Shared Redis:
 * Cluster/Instance shared across microservices (distributed locks, blacklist, rate limit) - Local
 * Redis: Dedicated instance for microservice-specific high-throughput caching (e.g. Geolocation in
 * ride-service)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.redis")
public class ShareRideRedisProperties {

    /** Master switch for Redis configuration in this microservice. */
    private boolean enabled = true;

    /**
     * Shared Redis configuration (Distributed Locks, Token Blacklist, Cross-service Coordination).
     */
    private RedisInstanceProperties shared = new RedisInstanceProperties(0);

    /** Local Dedicated Redis configuration (Service-specific internal caching). */
    private LocalRedisInstanceProperties local = new LocalRedisInstanceProperties(1);

    @Getter
    @Setter
    public static class RedisInstanceProperties {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database;
        private Duration timeout = Duration.ofSeconds(2);
        private boolean ssl = false;

        public RedisInstanceProperties() {
            this.database = 0;
        }

        public RedisInstanceProperties(int defaultDb) {
            this.database = defaultDb;
        }
    }

    @Getter
    @Setter
    public static class LocalRedisInstanceProperties extends RedisInstanceProperties {
        /** Whether this microservice has its own dedicated local Redis instance. */
        private boolean enabled = false;

        public LocalRedisInstanceProperties() {
            super(1);
        }

        public LocalRedisInstanceProperties(int defaultDb) {
            super(defaultDb);
        }
    }
}
