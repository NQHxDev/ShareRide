package com.shareride.gateway.service;

import com.shareride.gateway.ratelimit.RateLimitResult;
import com.shareride.gateway.ratelimit.RateLimitRule;
import com.shareride.gateway.ratelimit.RateLimitTier;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise contract for reactive rate limiting across ShareRide API Gateway.
 *
 * <p>Coordinates:
 *
 * <ul>
 *   <li>Tiered rate limiting (anonymous, free, and premium users)
 *   <li>Endpoint-specific limits (public login/register vs authenticated APIs)
 *   <li>Burst vs sustained limit enforcement via distributed Token Bucket
 *   <li>Standardized rate limit metadata computation (remaining tokens, reset timestamp)
 * </ul>
 */
public interface RateLimiterService {

    /**
     * Evaluates rate limiting for the incoming reactive exchange.
     *
     * @param exchange current server web exchange
     * @return Mono emitting RateLimitResult with decision, remaining tokens, and reset timestamp
     */
    Mono<RateLimitResult> evaluateRateLimit(ServerWebExchange exchange);

    /**
     * Resolves the user tier (ANONYMOUS, FREE, PREMIUM) for the request.
     *
     * @param exchange current server web exchange
     * @return resolved RateLimitTier
     */
    RateLimitTier resolveTier(ServerWebExchange exchange);

    /**
     * Resolves the effective rate limit rule (sustained and burst limits) for the request.
     *
     * @param exchange current server web exchange
     * @return matching RateLimitRule
     */
    RateLimitRule resolveRule(ServerWebExchange exchange);

    /**
     * Resolves sanitized client IP address from request headers or remote socket address.
     *
     * @param request current HTTP request
     * @return client IP string
     */
    String resolveClientIp(ServerHttpRequest request);
}
