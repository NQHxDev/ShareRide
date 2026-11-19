package com.shareride.gateway.config;

import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.ratelimit.EndpointRateLimitRule;
import com.shareride.gateway.ratelimit.RateLimitRule;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

/**
 * Enterprise Rate Limiting configuration for ShareRide API Gateway.
 *
 * <p>Creates beans for distributed rate limiting using Redis/Valkey token bucket algorithm with
 * pattern-based endpoint grouping for clean, externalized configuration.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Tiered rate limiting (anonymous, free, and premium users)
 *   <li>Pattern-based endpoint groups (no hardcoded endpoint lists)
 *   <li>Burst vs sustained limit configuration (token bucket replenish rate and capacity)
 *   <li>Standardized rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining,
 *       X-RateLimit-Reset)
 *   <li>Distributed KeyResolvers for Redis/Valkey Token Bucket Rate Limiting
 * </ul>
 *
 * <p>Configuration is loaded from {@link RateLimitProperties}.
 *
 * @see RateLimitProperties
 * @see RateLimitGroup
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
public class RateLimiterConfig {

    private final RateLimitProperties rateLimitProperties;
    private List<EndpointRateLimitRule> customEndpoints;

    public RateLimiterConfig() {
        this.rateLimitProperties = new RateLimitProperties();
    }

    public boolean isEnabled() {
        return rateLimitProperties.isEnabled();
    }

    public boolean isFailOpen() {
        return rateLimitProperties.isFailOpen();
    }

    public void setFailOpen(boolean failOpen) {
        rateLimitProperties.setFailOpen(failOpen);
    }

    public void setEnabled(boolean enabled) {
        rateLimitProperties.setEnabled(enabled);
    }

    public void setTimeoutMs(long timeoutMs) {
        rateLimitProperties.setTimeoutMs(timeoutMs);
    }

    public long getTimeoutMs() {
        return rateLimitProperties.getTimeoutMs();
    }

    public RateLimitRule getAnonymous() {
        return rateLimitProperties.getAnonymous();
    }

    public RateLimitRule getFree() {
        return rateLimitProperties.getFree();
    }

    public RateLimitRule getPremium() {
        return rateLimitProperties.getPremium();
    }

    public void setEndpoints(List<EndpointRateLimitRule> endpoints) {
        this.customEndpoints = endpoints;
    }

    public List<EndpointRateLimitRule> getEffectiveEndpoints() {
        if (customEndpoints != null) {
            return customEndpoints;
        }
        return endpointRateLimitRules();
    }

    /**
     * Lua script for atomic token bucket rate limiting in Redis/Valkey.
     *
     * <p>Implements the token bucket algorithm with:
     *
     * <ul>
     *   <li>Automatic token replenishment based on elapsed time
     *   <li>Burst capacity handling
     *   <li>Rate limit header values (remaining tokens, reset timestamp)
     *   <li>Retry-After calculation for rejected requests
     * </ul>
     */
    private static final String TOKEN_BUCKET_LUA_SCRIPT =
            """
            redis.replicate_commands()

            local tokens_key = KEYS[1]
            local timestamp_key = KEYS[2]

            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local fill_time = capacity / rate
            local ttl = math.floor(fill_time * 2)
            if ttl < 3600 then
              ttl = 3600
            end

            local last_tokens = tonumber(redis.call("get", tokens_key)) or capacity
            local last_refreshed = tonumber(redis.call("get", timestamp_key)) or 0

            local delta = math.max(0, now - last_refreshed)
            local filled_tokens = math.min(capacity, last_tokens + (delta * rate))
            local allowed = filled_tokens >= requested
            local new_tokens = allowed and (filled_tokens - requested) or filled_tokens

            local missing_tokens = math.max(0, capacity - new_tokens)
            local time_to_fill_seconds = math.ceil(missing_tokens / rate)
            local reset_timestamp = now + time_to_fill_seconds

            local retry_after = 0
            if not allowed then
              local needed_tokens = requested - filled_tokens
              retry_after = math.max(1, math.ceil(needed_tokens / rate))
            end

            if ttl > 0 then
              redis.call("setex", tokens_key, ttl, new_tokens)
              redis.call("setex", timestamp_key, ttl, now)
            end

            return { allowed and 1 or 0, math.floor(new_tokens), reset_timestamp, retry_after }
            """;

    /**
     * Provides access to rate limit properties for filters and other components.
     *
     * @return the injected rate limit properties
     */
    public RateLimitProperties rateLimitProperties() {
        return rateLimitProperties;
    }

    /**
     * Converts pattern-based rate limit groups to individual endpoint rules.
     *
     * <p>Each group's path patterns are expanded into separate {@link EndpointRateLimitRule}
     * instances with shared rate limit configuration. Falls back to default enterprise rules if no
     * groups are configured.
     *
     * @return list of endpoint-specific rate limit rules
     */
    @Bean
    public List<EndpointRateLimitRule> endpointRateLimitRules() {
        List<RateLimitGroup> groups = rateLimitProperties.getGroups();

        if (groups == null || groups.isEmpty()) {
            log.info(
                    "No rate limit groups configured; using default enterprise endpoint rules for"
                            + " auth and discovery endpoints");
            return createDefaultEndpointRules();
        }

        List<EndpointRateLimitRule> rules = new ArrayList<>();
        for (RateLimitGroup group : groups) {
            log.info(
                    "Loading rate limit group '{}' with {} path pattern(s): replenishRate={}, "
                            + "burstCapacity={}",
                    group.getName(),
                    group.getPathPatterns().size(),
                    group.getReplenishRate(),
                    group.getBurstCapacity());

            for (String pathPattern : group.getPathPatterns()) {
                EndpointRateLimitRule rule =
                        EndpointRateLimitRule.builder()
                                .pathPattern(pathPattern)
                                .method(group.getMethod())
                                .replenishRate(group.getReplenishRate())
                                .burstCapacity(group.getBurstCapacity())
                                .requestedTokens(group.getRequestedTokens())
                                .build();
                rules.add(rule);
            }
        }

        log.info(
                "Loaded {} endpoint rate limit rules from {} group(s)",
                rules.size(),
                groups.size());
        return rules;
    }

    private List<EndpointRateLimitRule> createDefaultEndpointRules() {
        return List.of(
                EndpointRateLimitRule.builder()
                        .pathPattern("/api/v1/auth/login")
                        .method("POST")
                        .replenishRate(5)
                        .burstCapacity(10)
                        .requestedTokens(1)
                        .build(),
                EndpointRateLimitRule.builder()
                        .pathPattern("/api/v1/auth/register")
                        .method("POST")
                        .replenishRate(5)
                        .burstCapacity(10)
                        .requestedTokens(1)
                        .build(),
                EndpointRateLimitRule.builder()
                        .pathPattern("/api/v1/auth/refresh")
                        .method("POST")
                        .replenishRate(10)
                        .burstCapacity(20)
                        .requestedTokens(1)
                        .build(),
                EndpointRateLimitRule.builder()
                        .pathPattern("/.well-known/**")
                        .replenishRate(50)
                        .burstCapacity(100)
                        .requestedTokens(1)
                        .build(),
                EndpointRateLimitRule.builder()
                        .pathPattern("/api/v1/auth/jwks.json")
                        .replenishRate(50)
                        .burstCapacity(100)
                        .requestedTokens(1)
                        .build());
    }

    /**
     * Distributed Redis Lua Script bean for atomic token bucket rate limiting.
     *
     * <p>Sets result type to {@code List.class} via reflection on {@link DefaultRedisScript} to
     * allow Spring Data Redis to deserialize the multi-bulk response into a {@code List<Long>}
     * while strictly adhering to Constitution Principle X (zero {@code @SuppressWarnings}).
     *
     * @return configured Redis script for rate limiting
     */
    @Bean
    public RedisScript<List<Long>> rateLimiterRedisScript() {
        DefaultRedisScript<List<Long>> redisScript =
                new DefaultRedisScript<>(TOKEN_BUCKET_LUA_SCRIPT);
        Field resultTypeField = ReflectionUtils.findField(DefaultRedisScript.class, "resultType");
        if (resultTypeField != null) {
            ReflectionUtils.makeAccessible(resultTypeField);
            ReflectionUtils.setField(resultTypeField, redisScript, List.class);
        }
        return redisScript;
    }

    /**
     * Resolves Rate Limiting key based on the client IP address (from X-Forwarded-For, X-Real-IP,
     * or remote socket address).
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor =
                    exchange.getRequest()
                            .getHeaders()
                            .getFirst(GatewayConstants.FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return Mono.just(forwardedFor.split(",")[0].trim());
            }

            String realIp =
                    exchange.getRequest().getHeaders().getFirst(GatewayConstants.REAL_IP_HEADER);
            if (realIp != null && !realIp.isBlank()) {
                return Mono.just(realIp.trim());
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }

            return Mono.just(GatewayConstants.UNKNOWN_IP);
        };
    }

    /**
     * Resolves Rate Limiting key based on the authenticated User ID header (X-User-Id), falling
     * back to client IP if unauthenticated.
     */
    @Bean
    public KeyResolver userKeyResolver(KeyResolver ipKeyResolver) {
        return exchange -> {
            String userId =
                    exchange.getRequest().getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return ipKeyResolver.resolve(exchange).map(ip -> "ip:" + ip);
        };
    }
}
