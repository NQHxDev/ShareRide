package com.shareride.gateway.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * Pattern-based rate limit group for endpoint configuration.
 *
 * <p>Groups multiple path patterns under a single named configuration with shared rate limit rules,
 * allowing clean, maintainable configuration in YAML without code changes.
 *
 * <h3>Example YAML Configuration:</h3>
 *
 * <pre>{@code
 * shareride:
 *   gateway:
 *     rate-limiting:
 *       groups:
 *         - name: "auth-endpoints"
 *           path-patterns:
 *             - "/api/v1/auth/login"
 *             - "/api/v1/auth/register"
 *           method: "POST"
 *           replenish-rate: 5
 *           burst-capacity: 10
 *         - name: "public-discovery"
 *           path-patterns:
 *             - "/.well-known/**"
 *             - "/api/v1/auth/jwks.json"
 *           replenish-rate: 50
 *           burst-capacity: 100
 * }</pre>
 */
@Data
public class RateLimitGroup {

    /**
     * Logical name for this rate limit group (e.g., "auth-endpoints", "public-discovery").
     *
     * <p>Used for logging and debugging purposes.
     */
    @NotBlank(message = "Rate limit group name is required")
    private String name;

    /**
     * List of path patterns covered by this group.
     *
     * <p>Supports Ant-style patterns: {@code /api/v1/auth/**}, {@code **.well-known/**}
     */
    @NotEmpty(message = "Rate limit group must specify at least one path pattern")
    private List<String> pathPatterns;

    /**
     * Optional HTTP method constraint (e.g., "POST", "GET").
     *
     * <p>If null, applies to all methods for the specified path patterns.
     */
    private String method;

    /**
     * Token bucket replenish rate (tokens per second).
     *
     * <p>Defines the sustained request rate allowed over time.
     */
    @Min(value = 1, message = "Replenish rate must be at least 1 token/second")
    private int replenishRate = 30;

    /**
     * Token bucket burst capacity (maximum tokens).
     *
     * <p>Defines the maximum burst of requests allowed before rate limiting kicks in.
     */
    @Min(value = 1, message = "Burst capacity must be at least 1 token")
    private int burstCapacity = 60;

    /**
     * Number of tokens consumed per request.
     *
     * <p>Typically 1, but can be higher for expensive operations.
     */
    @Min(value = 1, message = "Requested tokens must be at least 1")
    private int requestedTokens = 1;
}
