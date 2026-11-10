package com.shareride.gateway.config;

import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.ratelimit.EndpointRateLimitRule;
import com.shareride.gateway.ratelimit.RateLimitRule;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

/**
 * Enterprise Rate Limiting configuration for ShareRide API Gateway.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Tiered rate limiting (anonymous, free, and premium users)
 *   <li>Endpoint-specific limits (strict sensitive endpoints vs discovery)
 *   <li>Burst vs sustained limit configuration (token bucket replenish rate and capacity)
 *   <li>Standardized rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining,
 *       X-RateLimit-Reset)
 *   <li>Distributed KeyResolvers for Redis/Valkey Token Bucket Rate Limiting
 * </ul>
 */
@Getter
@Setter
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "shareride.gateway.rate-limiting")
public class RateLimiterConfig {

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

    /** Whether rate limiting is globally active. Defaults to {@code true}. */
    private boolean enabled = true;

    /**
     * Whether to fail-open when Redis/Valkey is temporarily unreachable or times out. Defaults to
     * {@code true} for enterprise resilience.
     */
    private boolean failOpen = true;

    /** Timeout in milliseconds for Redis token bucket operations. Defaults to 500ms. */
    private long timeoutMs = 500;

    /** Sustained and burst limits for unauthenticated anonymous IP clients. */
    private RateLimitRule anonymous = new RateLimitRule(10, 20, 1);

    /** Sustained and burst limits for standard free authenticated users. */
    private RateLimitRule free = new RateLimitRule(30, 60, 1);

    /** Sustained and burst limits for premium / privileged authenticated users. */
    private RateLimitRule premium = new RateLimitRule(100, 200, 1);

    /** Endpoint-specific rate limiting rules. */
    private List<EndpointRateLimitRule> endpoints = new ArrayList<>();

    /**
     * Resolves the effective list of endpoint rules, falling back to enterprise defaults if none
     * are explicitly declared via properties.
     */
    public List<EndpointRateLimitRule> getEffectiveEndpoints() {
        if (endpoints != null && !endpoints.isEmpty()) {
            return endpoints;
        }
        return createDefaultEndpoints();
    }

    private List<EndpointRateLimitRule> createDefaultEndpoints() {
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

    /** Distributed Redis Lua Script for atomic token bucket rate limiting. */
    @Bean
    public RedisScript<List<Long>> rateLimiterRedisScript() {
        @SuppressWarnings("unchecked")
        Class<List<Long>> resultType = (Class<List<Long>>) (Class<?>) List.class;
        return RedisScript.of(TOKEN_BUCKET_LUA_SCRIPT, resultType);
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
