package com.shareride.gateway.service.impl;

import com.shareride.gateway.config.RateLimiterConfig;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.ratelimit.EndpointRateLimitRule;
import com.shareride.gateway.ratelimit.InMemoryBucket;
import com.shareride.gateway.ratelimit.RateLimitResult;
import com.shareride.gateway.ratelimit.RateLimitRule;
import com.shareride.gateway.ratelimit.RateLimitTier;
import com.shareride.gateway.service.RateLimiterService;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise reactive implementation of {@link RateLimiterService}.
 *
 * <p>Enforces:
 *
 * <ul>
 *   <li>Tiered rate limiting: Anonymous (IP-based) vs Free users vs Premium users
 *   <li>Endpoint-specific limits: Sensitive auth endpoints (login, register) vs public discovery
 *   <li>Burst vs sustained limit management: Distributed Redis/Valkey Token Bucket
 *   <li>Standardized rate limit headers calculation (X-RateLimit-Limit, X-RateLimit-Remaining,
 *       X-RateLimit-Reset)
 *   <li>Enterprise resilience: Graceful fail-open degradation and in-memory fallback
 * </ul>
 */
@Slf4j
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private final RateLimiterConfig config;
    private final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<RedisScript<List<Long>>> redisScriptProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // In-memory fallback token bucket store for test contexts or Redis failover
    private final Map<String, InMemoryBucket> inMemoryBuckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterServiceImpl(
            RateLimiterConfig config,
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
            ObjectProvider<RedisScript<List<Long>>> redisScriptProvider) {
        this.config = config != null ? config : new RateLimiterConfig();
        this.redisTemplateProvider = redisTemplateProvider;
        this.redisScriptProvider = redisScriptProvider;
    }

    public RateLimiterServiceImpl(RateLimiterConfig config) {
        this(config, null, null);
    }

    @Override
    public Mono<RateLimitResult> evaluateRateLimit(ServerWebExchange exchange) {
        if (!config.isEnabled()) {
            return Mono.just(
                    RateLimitResult.builder()
                            .allowed(true)
                            .limit(-1)
                            .replenishRate(-1)
                            .remaining(-1)
                            .resetSeconds(0)
                            .retryAfterSeconds(0)
                            .tier("disabled")
                            .key("none")
                            .build());
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        String clientIp = resolveClientIp(request);
        String userId = request.getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
        String userRoles = request.getHeaders().getFirst(GatewayConstants.USER_ROLES_HEADER);
        String userTier = request.getHeaders().getFirst(GatewayConstants.USER_TIER_HEADER);

        // 1. Check for Endpoint-Specific Limit Rules
        EndpointRateLimitRule matchedEndpointRule = findMatchingEndpointRule(path, method);
        RateLimitRule rule;
        String key;
        String tierLabel;

        if (matchedEndpointRule != null) {
            rule =
                    new RateLimitRule(
                            matchedEndpointRule.getReplenishRate(),
                            matchedEndpointRule.getBurstCapacity(),
                            matchedEndpointRule.getRequestedTokens());
            String subject =
                    (userId != null && !userId.isBlank()) ? "u:" + userId : "ip:" + clientIp;
            key = "rl:ep:" + matchedEndpointRule.getPathPattern() + ":" + subject;
            tierLabel = "endpoint:" + matchedEndpointRule.getPathPattern();
        } else {
            // 2. Evaluate Tiered Rate Limiting (Anonymous vs Free vs Premium)
            RateLimitTier tier = resolveTier(userId, userRoles, userTier);
            tierLabel = tier.getValue();

            if (tier == RateLimitTier.PREMIUM) {
                rule = config.getPremium();
                key = "rl:tier:prem:u:" + userId;
            } else if (tier == RateLimitTier.FREE) {
                rule = config.getFree();
                key = "rl:tier:free:u:" + userId;
            } else {
                rule = config.getAnonymous();
                key = "rl:tier:anon:ip:" + clientIp;
            }
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        ReactiveStringRedisTemplate redisTemplate =
                (redisTemplateProvider != null) ? redisTemplateProvider.getIfAvailable() : null;
        RedisScript<List<Long>> redisScript =
                (redisScriptProvider != null) ? redisScriptProvider.getIfAvailable() : null;

        // 3. Distributed Redis/Valkey Token Bucket Execution
        if (redisTemplate != null && redisScript != null) {
            List<String> keys = List.of(key + ":tokens", key + ":ts");
            List<String> args =
                    List.of(
                            String.valueOf(rule.getReplenishRate()),
                            String.valueOf(rule.getBurstCapacity()),
                            String.valueOf(nowEpochSeconds),
                            String.valueOf(rule.getRequestedTokens()));

            return redisTemplate
                    .execute(redisScript, keys, args)
                    .next()
                    .map(list -> parseRedisResult(list, rule, tierLabel, key, nowEpochSeconds))
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .onErrorResume(
                            ex -> {
                                log.warn(
                                        "Redis rate limiter failed for key [{}] ({}), activating"
                                                + " fail-open resilience",
                                        key,
                                        ex.getMessage());
                                return handleRedisFallback(rule, tierLabel, key, nowEpochSeconds);
                            });
        }

        // 4. Standalone In-Memory Token Bucket Fallback
        return Mono.just(evaluateInMemory(key, rule, tierLabel, nowEpochSeconds));
    }

    @Override
    public RateLimitTier resolveTier(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
        String userRoles = request.getHeaders().getFirst(GatewayConstants.USER_ROLES_HEADER);
        String userTier = request.getHeaders().getFirst(GatewayConstants.USER_TIER_HEADER);
        return resolveTier(userId, userRoles, userTier);
    }

    private RateLimitTier resolveTier(String userId, String userRoles, String userTier) {
        if (userId == null || userId.isBlank()) {
            return RateLimitTier.ANONYMOUS;
        }
        if ((userTier != null && userTier.equalsIgnoreCase(GatewayConstants.TIER_PREMIUM))
                || hasPremiumRole(userRoles)) {
            return RateLimitTier.PREMIUM;
        }
        return RateLimitTier.FREE;
    }

    @Override
    public RateLimitRule resolveRule(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        EndpointRateLimitRule epRule = findMatchingEndpointRule(path, method);
        if (epRule != null) {
            return new RateLimitRule(
                    epRule.getReplenishRate(),
                    epRule.getBurstCapacity(),
                    epRule.getRequestedTokens());
        }

        RateLimitTier tier = resolveTier(exchange);
        if (tier == RateLimitTier.PREMIUM) {
            return config.getPremium();
        } else if (tier == RateLimitTier.ANONYMOUS) {
            return config.getAnonymous();
        }
        return config.getFree();
    }

    private EndpointRateLimitRule findMatchingEndpointRule(String path, String method) {
        List<EndpointRateLimitRule> rules = config.getEffectiveEndpoints();
        for (EndpointRateLimitRule rule : rules) {
            if (rule.getPathPattern() != null && pathMatcher.match(rule.getPathPattern(), path)) {
                if (rule.getMethod() == null
                        || rule.getMethod().isBlank()
                        || rule.getMethod().equalsIgnoreCase(method)) {
                    return rule;
                }
            }
        }
        return null;
    }

    private boolean hasPremiumRole(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        String[] roles = rolesHeader.split(",");
        for (String role : roles) {
            String trimmed = role.trim();
            if (trimmed.equalsIgnoreCase("ROLE_PREMIUM")
                    || trimmed.equalsIgnoreCase("PREMIUM")
                    || trimmed.equalsIgnoreCase("ROLE_ADMIN")
                    || trimmed.equalsIgnoreCase("ADMIN")
                    || trimmed.equalsIgnoreCase("ROLE_VIP")
                    || trimmed.equalsIgnoreCase("VIP")) {
                return true;
            }
        }
        return false;
    }

    private RateLimitResult parseRedisResult(
            List<Long> result,
            RateLimitRule rule,
            String tierLabel,
            String key,
            long nowEpochSeconds) {
        if (result == null || result.size() < 4) {
            return handleRedisFallback(rule, tierLabel, key, nowEpochSeconds).block();
        }

        boolean allowed = result.get(0) != null && result.get(0) == 1L;
        long remaining = result.get(1) != null ? result.get(1) : 0L;
        long resetSeconds = result.get(2) != null ? result.get(2) : nowEpochSeconds;
        long retryAfter = result.get(3) != null ? result.get(3) : 0L;

        return RateLimitResult.builder()
                .allowed(allowed)
                .limit(rule.getBurstCapacity())
                .replenishRate(rule.getReplenishRate())
                .remaining(remaining)
                .resetSeconds(resetSeconds)
                .retryAfterSeconds(retryAfter)
                .tier(tierLabel)
                .key(key)
                .build();
    }

    private Mono<RateLimitResult> handleRedisFallback(
            RateLimitRule rule, String tierLabel, String key, long nowEpochSeconds) {
        if (config.isFailOpen()) {
            return Mono.just(
                    RateLimitResult.builder()
                            .allowed(true)
                            .limit(rule.getBurstCapacity())
                            .replenishRate(rule.getReplenishRate())
                            .remaining(rule.getBurstCapacity())
                            .resetSeconds(nowEpochSeconds)
                            .retryAfterSeconds(0)
                            .tier(tierLabel)
                            .key(key)
                            .build());
        }
        return Mono.just(evaluateInMemory(key, rule, tierLabel, nowEpochSeconds));
    }

    private RateLimitResult evaluateInMemory(
            String key, RateLimitRule rule, String tierLabel, long nowEpochSeconds) {
        double rate = rule.getReplenishRate();
        double capacity = rule.getBurstCapacity();
        int requested = rule.getRequestedTokens();

        InMemoryBucket bucket =
                inMemoryBuckets.computeIfAbsent(
                        key, k -> new InMemoryBucket(capacity, nowEpochSeconds));

        synchronized (bucket) {
            double delta = Math.max(0, nowEpochSeconds - bucket.getLastRefreshed());
            double filledTokens = Math.min(capacity, bucket.getTokens() + (delta * rate));
            boolean allowed = filledTokens >= requested;
            double newTokens = allowed ? filledTokens - requested : filledTokens;

            bucket.setTokens(newTokens);
            bucket.setLastRefreshed(nowEpochSeconds);

            double missing = Math.max(0, capacity - newTokens);
            long timeToFill = (long) Math.ceil(missing / rate);
            long resetTimestamp = nowEpochSeconds + timeToFill;

            long retryAfter = 0;
            if (!allowed) {
                double needed = requested - filledTokens;
                retryAfter = Math.max(1, (long) Math.ceil(needed / rate));
            }

            return RateLimitResult.builder()
                    .allowed(allowed)
                    .limit((long) capacity)
                    .replenishRate((long) rate)
                    .remaining((long) Math.floor(newTokens))
                    .resetSeconds(resetTimestamp)
                    .retryAfterSeconds(retryAfter)
                    .tier(tierLabel)
                    .key(key)
                    .build();
        }
    }

    @Override
    public String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(GatewayConstants.FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst(GatewayConstants.REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return GatewayConstants.UNKNOWN_IP;
    }
}
