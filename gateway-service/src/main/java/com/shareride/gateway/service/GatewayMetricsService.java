package com.shareride.gateway.service;

import java.time.Duration;

/**
 * Enterprise contract for recording custom metrics across ShareRide API Gateway.
 *
 * <p>Provides standardized Prometheus metrics for request routing latencies, in-flight active
 * requests, rate limiting rejections, circuit breaker fallbacks, authentication events, and gateway
 * exception handling.
 */
public interface GatewayMetricsService {

    /**
     * Records completed HTTP request duration, counter, status family, and slow request thresholds.
     *
     * @param routeId target gateway route ID (or "unmatched")
     * @param method HTTP method (e.g. GET, POST)
     * @param path request path URI
     * @param statusCode HTTP status code (e.g. 200, 401, 503)
     * @param duration total elapsed request duration
     */
    void recordRequest(
            String routeId, String method, String path, int statusCode, Duration duration);

    /**
     * Increments the in-flight concurrent requests gauge.
     *
     * @param routeId target gateway route ID (or "unmatched")
     */
    void incrementActiveRequests(String routeId);

    /**
     * Decrements the in-flight concurrent requests gauge.
     *
     * @param routeId target gateway route ID (or "unmatched")
     */
    void decrementActiveRequests(String routeId);

    /**
     * Returns the current total in-flight active requests.
     *
     * @return current active count
     */
    int getActiveRequests();

    /**
     * Returns the current in-flight active requests for a specific route.
     *
     * @param routeId target gateway route ID
     * @return current active count for the route
     */
    int getActiveRequests(String routeId);

    /**
     * Records a rate-limiting rejection event (HTTP 429).
     *
     * @param routeId target gateway route ID
     * @param clientIpPrefix sanitized client IP prefix
     * @param limitType type of rate limit (e.g. "ip", "user")
     */
    void recordRateLimitExceeded(String routeId, String clientIpPrefix, String limitType);

    /**
     * Records a rate-limiting allowed event.
     *
     * @param routeId target gateway route ID
     * @param limitType type of rate limit (e.g. "ip", "user")
     */
    void recordRateLimitAllowed(String routeId, String limitType);

    /**
     * Records a circuit breaker fallback execution.
     *
     * @param routeId target gateway route ID
     * @param circuitBreakerName name of the circuit breaker instance
     * @param reason trigger reason (e.g. "timeout", "open_state")
     */
    void recordCircuitBreakerFallback(String routeId, String circuitBreakerName, String reason);

    /**
     * Records an authentication event at the gateway perimeter.
     *
     * @param eventType type of event (e.g. "PUBLIC_BYPASS", "TOKEN_PRESENT", "MISSING_TOKEN")
     * @param path requested path
     * @param reason context reason
     */
    void recordAuthEvent(String eventType, String path, String reason);

    /**
     * Records a gateway-level exception handled by GatewayErrorWebExceptionHandler.
     *
     * @param routeId target gateway route ID
     * @param path requested path
     * @param statusCode HTTP status code
     * @param errorCode error code name
     */
    void recordGatewayError(String routeId, String path, int statusCode, String errorCode);

    /**
     * Records request payload size distribution summary in bytes.
     *
     * @param routeId target gateway route ID
     * @param bytes request payload content-length in bytes
     */
    void recordRequestBodySize(String routeId, long bytes);

    /**
     * Records response payload size distribution summary in bytes.
     *
     * @param routeId target gateway route ID
     * @param bytes response payload content-length in bytes
     */
    void recordResponseBodySize(String routeId, long bytes);
}
