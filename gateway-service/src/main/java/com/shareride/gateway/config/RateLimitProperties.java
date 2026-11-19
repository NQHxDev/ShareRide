package com.shareride.gateway.config;

import com.shareride.gateway.ratelimit.RateLimitRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for API Gateway rate limiting.
 *
 * <p>Supports tiered rate limiting (anonymous, free, premium) and pattern-based endpoint grouping
 * for flexible, externalized rate limit configuration without code changes.
 *
 * <p>Properties are bound from {@code shareride.gateway.rate-limiting.*} configuration namespace.
 *
 * @see RateLimitGroup
 */
@Data
@Validated
@ConfigurationProperties(prefix = "shareride.gateway.rate-limiting")
public class RateLimitProperties {

    /** Whether rate limiting is globally active. Defaults to {@code true}. */
    private boolean enabled = true;

    /**
     * Whether to fail-open when Redis/Valkey is temporarily unreachable or times out. Defaults to
     * {@code true} for enterprise resilience.
     */
    private boolean failOpen = true;

    /** Timeout in milliseconds for Redis token bucket operations. Defaults to 500ms. */
    @Min(value = 100, message = "Rate limiter timeout must be at least 100ms")
    private long timeoutMs = 500;

    /** Sustained and burst limits for unauthenticated anonymous IP clients. */
    @NotNull @Valid private RateLimitRule anonymous = new RateLimitRule(10, 20, 1);

    /** Sustained and burst limits for standard free authenticated users. */
    @NotNull @Valid private RateLimitRule free = new RateLimitRule(30, 60, 1);

    /** Sustained and burst limits for premium / privileged authenticated users. */
    @NotNull @Valid private RateLimitRule premium = new RateLimitRule(100, 200, 1);

    /**
     * Pattern-based endpoint groups for rate limiting configuration.
     *
     * <p>Each group can specify multiple path patterns with shared rate limit rules, enabling clean
     * configuration without endpoint proliferation.
     */
    @Valid private List<RateLimitGroup> groups = new ArrayList<>();
}
