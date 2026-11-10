package com.shareride.gateway.service.impl;

import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Production-ready implementation of {@link GatewayMetricsService}.
 *
 * <p>Emits Micrometer custom metrics with standard 'shareride.' prefix, Prometheus Exemplar
 * compatibility, SLO histogram latency percentiles, and concurrent in-flight gauge tracking.
 */
@Slf4j
@Service
public class GatewayMetricsServiceImpl implements GatewayMetricsService {

    private final ShareRideMetrics shareRideMetrics;

    private final AtomicInteger globalActiveRequests = new AtomicInteger(0);
    private final Map<String, AtomicInteger> routeActiveRequests = new ConcurrentHashMap<>();

    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000L;

    public GatewayMetricsServiceImpl(
            MeterRegistry meterRegistry,
            ObjectProvider<ShareRideMetrics> shareRideMetricsProvider) {
        this.shareRideMetrics =
                shareRideMetricsProvider.getIfAvailable(() -> new ShareRideMetrics(meterRegistry));

        // Register global active in-flight requests gauge
        this.shareRideMetrics.registerAtomicInteger(
                GatewayConstants.METRIC_GATEWAY_REQUESTS_ACTIVE,
                "Current in-flight requests in API Gateway",
                globalActiveRequests,
                "scope",
                "global");
    }

    @Override
    public void recordRequest(
            String routeId, String method, String path, int statusCode, Duration duration) {
        String safeRoute = sanitize(routeId, "unmatched");
        String safeMethod = sanitize(method, "UNKNOWN");
        String statusStr = String.valueOf(statusCode);
        String statusGroup = resolveStatusGroup(statusCode);
        String outcome = resolveOutcome(statusCode);

        // 1. Record gateway request counter
        shareRideMetrics.incrementCounter(
                GatewayConstants.METRIC_GATEWAY_REQUESTS_TOTAL,
                GatewayConstants.TAG_ROUTE_ID,
                safeRoute,
                GatewayConstants.TAG_METHOD,
                safeMethod,
                GatewayConstants.TAG_STATUS,
                statusStr,
                GatewayConstants.TAG_STATUS_GROUP,
                statusGroup,
                GatewayConstants.TAG_OUTCOME,
                outcome);

        // 2. Record gateway request duration timer (with SLO histogram & percentiles)
        if (duration != null) {
            shareRideMetrics.recordTime(
                    GatewayConstants.METRIC_GATEWAY_REQUESTS_DURATION,
                    duration,
                    GatewayConstants.TAG_ROUTE_ID,
                    safeRoute,
                    GatewayConstants.TAG_METHOD,
                    safeMethod,
                    GatewayConstants.TAG_STATUS,
                    statusStr,
                    GatewayConstants.TAG_STATUS_GROUP,
                    statusGroup,
                    GatewayConstants.TAG_OUTCOME,
                    outcome);

            // 3. Record shared HTTP request metric
            shareRideMetrics.recordHttpRequest(safeMethod, safeRoute, statusCode, duration);

            // 4. Detect and record slow requests exceeding threshold
            if (duration.toMillis() >= SLOW_REQUEST_THRESHOLD_MS) {
                shareRideMetrics.incrementCounter(
                        GatewayConstants.METRIC_GATEWAY_REQUESTS_SLOW,
                        GatewayConstants.TAG_ROUTE_ID,
                        safeRoute,
                        GatewayConstants.TAG_METHOD,
                        safeMethod,
                        GatewayConstants.TAG_STATUS,
                        statusStr);
                shareRideMetrics.recordSlowRequest(
                        safeMethod, path != null ? path : "/", statusCode);
            }
        }
    }

    @Override
    public void incrementActiveRequests(String routeId) {
        globalActiveRequests.incrementAndGet();
        String safeRoute = sanitize(routeId, "unmatched");
        routeActiveRequests
                .computeIfAbsent(
                        safeRoute,
                        r -> {
                            AtomicInteger routeCounter = new AtomicInteger(0);
                            shareRideMetrics.registerAtomicInteger(
                                    GatewayConstants.METRIC_GATEWAY_REQUESTS_ACTIVE,
                                    "Current in-flight requests for route in API Gateway",
                                    routeCounter,
                                    "scope",
                                    "route",
                                    GatewayConstants.TAG_ROUTE_ID,
                                    r);
                            return routeCounter;
                        })
                .incrementAndGet();
    }

    @Override
    public void decrementActiveRequests(String routeId) {
        globalActiveRequests.updateAndGet(val -> Math.max(0, val - 1));
        String safeRoute = sanitize(routeId, "unmatched");
        AtomicInteger routeCounter = routeActiveRequests.get(safeRoute);
        if (routeCounter != null) {
            routeCounter.updateAndGet(val -> Math.max(0, val - 1));
        }
    }

    @Override
    public int getActiveRequests() {
        return globalActiveRequests.get();
    }

    @Override
    public int getActiveRequests(String routeId) {
        String safeRoute = sanitize(routeId, "unmatched");
        AtomicInteger routeCounter = routeActiveRequests.get(safeRoute);
        return routeCounter != null ? routeCounter.get() : 0;
    }

    @Override
    public void recordRateLimitExceeded(String routeId, String clientIpPrefix, String limitType) {
        String safeRoute = sanitize(routeId, "unmatched");
        String safeIpPrefix = sanitizeIp(clientIpPrefix);
        String safeLimitType = sanitize(limitType, "ip");

        shareRideMetrics.incrementCounter(
                GatewayConstants.METRIC_GATEWAY_RATELIMIT_REJECTED,
                GatewayConstants.TAG_ROUTE_ID,
                safeRoute,
                GatewayConstants.TAG_LIMIT_TYPE,
                safeLimitType,
                GatewayConstants.TAG_CLIENT_IP_PREFIX,
                safeIpPrefix);

        // Feed standard security rate limit alert:
        // shareride_security_ratelimit_exceeded_total
        shareRideMetrics.recordRateLimitExceeded(safeIpPrefix, safeRoute, safeLimitType);
    }

    @Override
    public void recordRateLimitAllowed(String routeId, String limitType) {
        String safeRoute = sanitize(routeId, "unmatched");
        String safeLimitType = sanitize(limitType, "ip");

        shareRideMetrics.incrementCounter(
                GatewayConstants.METRIC_GATEWAY_RATELIMIT_ALLOWED,
                GatewayConstants.TAG_ROUTE_ID,
                safeRoute,
                GatewayConstants.TAG_LIMIT_TYPE,
                safeLimitType);
    }

    @Override
    public void recordCircuitBreakerFallback(
            String routeId, String circuitBreakerName, String reason) {
        String safeRoute = sanitize(routeId, "unmatched");
        String safeCbName = sanitize(circuitBreakerName, "default");
        String safeReason = sanitize(reason, "unknown");

        shareRideMetrics.incrementCounter(
                GatewayConstants.METRIC_GATEWAY_CIRCUITBREAKER_FALLBACK,
                GatewayConstants.TAG_ROUTE_ID,
                safeRoute,
                GatewayConstants.TAG_CIRCUIT_BREAKER,
                safeCbName,
                GatewayConstants.TAG_REASON,
                safeReason);

        shareRideMetrics.recordFallback(safeCbName, safeReason);
    }

    @Override
    public void recordAuthEvent(String eventType, String path, String reason) {
        String safeEvent = sanitize(eventType, "UNKNOWN");
        String safePath = sanitizePath(path);
        String safeReason = sanitize(reason, "none");

        shareRideMetrics.incrementCounter(
                GatewayConstants.METRIC_GATEWAY_AUTH_EVENTS,
                GatewayConstants.TAG_EVENT_TYPE,
                safeEvent,
                GatewayConstants.TAG_PATH,
                safePath,
                GatewayConstants.TAG_REASON,
                safeReason);

        if ("MISSING_TOKEN".equalsIgnoreCase(safeEvent)
                || "INVALID_TOKEN".equalsIgnoreCase(safeEvent)
                || "AUTH_FAILURE".equalsIgnoreCase(safeEvent)) {
            shareRideMetrics.recordAuthFailure("jwt", safeReason, "unknown");
        }
    }

    @Override
    public void recordGatewayError(String routeId, String path, int statusCode, String errorCode) {
        String safeRoute = sanitize(routeId, "unmatched");
        String safePath = sanitizePath(path);
        String safeErrorCode = sanitize(errorCode, "INTERNAL_ERROR");

        shareRideMetrics.incrementCounter(
                GatewayConstants.METRIC_GATEWAY_ERRORS_TOTAL,
                GatewayConstants.TAG_ROUTE_ID,
                safeRoute,
                GatewayConstants.TAG_PATH,
                safePath,
                GatewayConstants.TAG_STATUS,
                String.valueOf(statusCode),
                GatewayConstants.TAG_ERROR_CODE,
                safeErrorCode);

        shareRideMetrics.recordException(safeErrorCode, "GatewayException");
    }

    @Override
    public void recordRequestBodySize(String routeId, long bytes) {
        if (bytes > 0) {
            shareRideMetrics.recordSummary(
                    GatewayConstants.METRIC_GATEWAY_REQUEST_SIZE,
                    bytes,
                    GatewayConstants.TAG_ROUTE_ID,
                    sanitize(routeId, "unmatched"));
        }
    }

    @Override
    public void recordResponseBodySize(String routeId, long bytes) {
        if (bytes > 0) {
            shareRideMetrics.recordSummary(
                    GatewayConstants.METRIC_GATEWAY_RESPONSE_SIZE,
                    bytes,
                    GatewayConstants.TAG_ROUTE_ID,
                    sanitize(routeId, "unmatched"));
        }
    }

    private String resolveStatusGroup(int status) {
        if (status >= 200 && status < 300) {
            return "2xx";
        } else if (status >= 300 && status < 400) {
            return "3xx";
        } else if (status >= 400 && status < 500) {
            return "4xx";
        } else if (status >= 500 && status < 600) {
            return "5xx";
        }
        return "unknown";
    }

    private String resolveOutcome(int status) {
        if (status >= 200 && status < 300) {
            return "SUCCESS";
        } else if (status >= 300 && status < 400) {
            return "REDIRECTION";
        } else if (status >= 400 && status < 500) {
            return "CLIENT_ERROR";
        } else if (status >= 500 && status < 600) {
            return "SERVER_ERROR";
        }
        return "UNKNOWN";
    }

    private String sanitize(String value, String defaultValue) {
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    private String sanitizeIp(String ip) {
        if (ip == null || ip.isBlank() || GatewayConstants.UNKNOWN_IP.equalsIgnoreCase(ip)) {
            return "unknown";
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".0/24";
        }
        return ip;
    }

    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final java.util.regex.Pattern NUMERIC_ID_PATTERN =
            java.util.regex.Pattern.compile("/\\d+");

    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        int queryIdx = path.indexOf('?');
        String cleanPath = (queryIdx >= 0) ? path.substring(0, queryIdx) : path;
        cleanPath = UUID_PATTERN.matcher(cleanPath).replaceAll("{id}");
        cleanPath = NUMERIC_ID_PATTERN.matcher(cleanPath).replaceAll("/{id}");
        return cleanPath;
    }
}
