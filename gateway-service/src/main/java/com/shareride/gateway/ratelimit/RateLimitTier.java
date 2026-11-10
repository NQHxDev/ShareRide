package com.shareride.gateway.ratelimit;

import com.shareride.gateway.constant.GatewayConstants;

/** Rate limiting tier classifications for ShareRide clients. */
public enum RateLimitTier {
    ANONYMOUS(GatewayConstants.TIER_ANONYMOUS),
    FREE(GatewayConstants.TIER_FREE),
    PREMIUM(GatewayConstants.TIER_PREMIUM);

    private final String value;

    RateLimitTier(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RateLimitTier fromString(String tier) {
        if (tier == null || tier.isBlank()) {
            return FREE;
        }
        for (RateLimitTier t : values()) {
            if (t.name().equalsIgnoreCase(tier.trim()) || t.value.equalsIgnoreCase(tier.trim())) {
                return t;
            }
        }
        return FREE;
    }
}
