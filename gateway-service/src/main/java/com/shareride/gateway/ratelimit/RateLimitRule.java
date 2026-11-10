package com.shareride.gateway.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Token bucket rate limit parameters defining sustained rate and burst capacity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {

    /** Sustained rate: tokens replenished per second. */
    private int replenishRate;

    /** Burst capacity: maximum token bucket capacity to absorb sudden traffic spikes. */
    private int burstCapacity;

    /** Number of tokens requested per call (default 1). */
    @Builder.Default private int requestedTokens = 1;
}
