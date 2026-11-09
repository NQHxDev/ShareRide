package com.shareride.gateway.filter;

import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.dynamic.DynamicLogLevelContext;
import com.shareride.common.logging.masking.DataMaskingUtils;
import com.shareride.common.logging.sampling.LogSampler;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import java.net.InetSocketAddress;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise Reactive HTTP Access Logging and Telemetry GlobalFilter for ShareRide API Gateway.
 *
 * <p>Fully integrates with the core {@code com.shareride.common.logging} framework:
 *
 * <ul>
 *   <li>Configurable via {@link ShareRideLoggingProperties} (thresholds, sampling, exclude paths).
 *   <li>PII & secret sanitization for URLs and sensitive request headers via {@link
 *       DataMaskingUtils}.
 *   <li>Intelligent log sampling for high-throughput traffic via {@link LogSampler}.
 *   <li>Dynamic per-request and per-user debug override via {@link DynamicLogLevelContext}.
 *   <li>SLF4J MDC synchronization using {@link MdcConstants} for unified structured JSON logging.
 *   <li>Slow request telemetry detection and metrics recording via {@link GatewayMetricsService}.
 * </ul>
 */
@Slf4j
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private final ShareRideLoggingProperties properties;
    private final LogSampler sampler;
    private final Optional<GatewayMetricsService> metricsService;
    private final Optional<ShareRideMetrics> shareRideMetrics;
    private final Optional<AuditLogService> auditLogService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestLoggingGlobalFilter(
            ShareRideLoggingProperties properties,
            ObjectProvider<LogSampler> samplerProvider,
            ObjectProvider<GatewayMetricsService> metricsServiceProvider,
            ObjectProvider<ShareRideMetrics> shareRideMetricsProvider,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.properties = properties != null ? properties : new ShareRideLoggingProperties();
        this.sampler =
                (samplerProvider != null && samplerProvider.getIfAvailable() != null)
                        ? samplerProvider.getIfAvailable()
                        : new LogSampler(
                                this.properties.getInfoSampleRate(),
                                this.properties.getDebugSampleRate());
        this.metricsService =
                Optional.ofNullable(
                        metricsServiceProvider != null
                                ? metricsServiceProvider.getIfAvailable()
                                : null);
        this.shareRideMetrics =
                Optional.ofNullable(
                        shareRideMetricsProvider != null
                                ? shareRideMetricsProvider.getIfAvailable()
                                : null);
        this.auditLogService =
                Optional.ofNullable(
                        auditLogServiceProvider != null
                                ? auditLogServiceProvider.getIfAvailable()
                                : null);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isExcluded(path)) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String method = request.getMethod().name();
        String queryString = request.getURI().getRawQuery();
        String rawFullPath =
                (queryString != null && !queryString.isBlank()) ? path + "?" + queryString : path;
        String fullPath =
                properties.isMaskUrlParameters()
                        ? DataMaskingUtils.maskUrl(rawFullPath)
                        : rawFullPath;

        String correlationId = resolveCorrelationId(exchange);
        String clientIp = resolveClientIp(request);
        String userId = resolveUserId(request);
        String traceId = resolveTraceId(request);

        boolean debugOverride = checkAndSetDebugOverride(request, userId);

        if (properties.isIncludeHeaders() && log.isDebugEnabled()) {
            logHeaders(request);
        }

        return chain.filter(exchange)
                .doFinally(
                        signalType -> {
                            long durationMs = System.currentTimeMillis() - startTime;
                            HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                            int statusValue = (statusCode != null) ? statusCode.value() : 500;
                            boolean isSlow = durationMs >= properties.getSlowThresholdMs();

                            populateMdc(correlationId, clientIp, userId, method, path, traceId);
                            try {
                                if (isSlow) {
                                    log.warn(
                                            "[SLOW REQUEST] {} {} status={} took {}ms (threshold:"
                                                    + " {}ms) clientIp={} userId={}",
                                            method,
                                            fullPath,
                                            statusValue,
                                            durationMs,
                                            properties.getSlowThresholdMs(),
                                            clientIp,
                                            userId);
                                    shareRideMetrics.ifPresent(
                                            m -> m.recordSlowRequest(method, path, statusValue));
                                } else if (statusValue >= 500) {
                                    log.error(
                                            "[HTTP] {} {} status={} duration={}ms clientIp={}"
                                                    + " userId={}",
                                            method,
                                            fullPath,
                                            statusValue,
                                            durationMs,
                                            clientIp,
                                            userId);
                                } else if (statusValue >= 400) {
                                    log.warn(
                                            "[HTTP] {} {} status={} duration={}ms clientIp={}"
                                                    + " userId={}",
                                            method,
                                            fullPath,
                                            statusValue,
                                            durationMs,
                                            clientIp,
                                            userId);
                                } else {
                                    if (sampler.shouldSample("INFO", traceId, false)) {
                                        log.info(
                                                "[HTTP] {} {} status={} duration={}ms clientIp={}"
                                                        + " userId={}",
                                                method,
                                                fullPath,
                                                statusValue,
                                                durationMs,
                                                clientIp,
                                                userId);
                                    }
                                }
                            } finally {
                                clearMdc(traceId);
                                if (debugOverride) {
                                    DynamicLogLevelContext.clear();
                                }
                            }
                        });
    }

    private boolean isExcluded(String path) {
        if (properties.getExcludePaths() != null) {
            for (String pattern : properties.getExcludePaths()) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkAndSetDebugOverride(ServerHttpRequest request, String userId) {
        String debugHeader =
                request.getHeaders().getFirst(DynamicLogLevelContext.DEBUG_REQUEST_HEADER);
        boolean isDebug =
                "true".equalsIgnoreCase(debugHeader) || DynamicLogLevelContext.isDebugUser(userId);
        if (isDebug) {
            DynamicLogLevelContext.setDebugOverride(true);
            return true;
        }
        return false;
    }

    private String resolveCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId =
                    exchange.getRequest()
                            .getHeaders()
                            .getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        }
        return (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : "unknown-correlation-id";
    }

    private String resolveUserId(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
        return (userId != null && !userId.isBlank())
                ? userId.trim()
                : GatewayConstants.ANONYMOUS_USER;
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId.trim();
        }
        String traceParent = request.getHeaders().getFirst("traceparent");
        if (traceParent != null && traceParent.startsWith("00-") && traceParent.length() >= 35) {
            return traceParent.substring(3, 35);
        }
        return null;
    }

    private String resolveClientIp(ServerHttpRequest request) {
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

    private void logHeaders(ServerHttpRequest request) {
        StringBuilder sb = new StringBuilder("Headers: [");
        HttpHeaders headers = request.getHeaders();
        headers.forEach(
                (name, values) -> {
                    String value;
                    if ((properties.getSensitiveHeaders() != null
                                    && properties
                                            .getSensitiveHeaders()
                                            .contains(name.toLowerCase()))
                            || DataMaskingUtils.isSensitive(name)) {
                        value = DataMaskingUtils.MASK;
                    } else {
                        value = String.join(", ", values);
                    }
                    sb.append(name).append("=").append(value).append(", ");
                });
        sb.append("]");
        log.debug(sb.toString());
    }

    private void populateMdc(
            String correlationId,
            String clientIp,
            String userId,
            String method,
            String path,
            String traceId) {
        MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        MDC.put(MdcConstants.CLIENT_IP, clientIp);
        MDC.put(MdcConstants.USER_ID, userId);
        MDC.put(MdcConstants.HTTP_METHOD, method);
        MDC.put(MdcConstants.HTTP_URI, path);
        MDC.put(MdcConstants.SERVICE_NAME, "gateway-service");
        if (traceId != null) {
            MDC.put(MdcConstants.TRACE_ID, traceId);
        }
    }

    private void clearMdc(String traceId) {
        MDC.remove(MdcConstants.CORRELATION_ID);
        MDC.remove(MdcConstants.CLIENT_IP);
        MDC.remove(MdcConstants.USER_ID);
        MDC.remove(MdcConstants.HTTP_METHOD);
        MDC.remove(MdcConstants.HTTP_URI);
        MDC.remove(MdcConstants.SERVICE_NAME);
        if (traceId != null) {
            MDC.remove(MdcConstants.TRACE_ID);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
