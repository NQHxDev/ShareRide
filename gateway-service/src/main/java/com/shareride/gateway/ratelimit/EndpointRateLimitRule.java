package com.shareride.gateway.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Route- or endpoint-specific rate limiting rule with fine-grained path matching. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointRateLimitRule {

    /** Ant path pattern to match, e.g. {@code /api/v1/auth/login} or {@code /.well-known/**}. */
    private String pathPattern;

    /** Optional HTTP method filter (e.g. POST, GET) or null/empty for any method. */
    private String method;

    /** Sustained rate: tokens replenished per second. */
    private int replenishRate;

    /** Burst capacity: maximum token bucket capacity. */
    private int burstCapacity;

    /** Number of tokens requested per call (default 1). */
    @Builder.Default private int requestedTokens = 1;
}
