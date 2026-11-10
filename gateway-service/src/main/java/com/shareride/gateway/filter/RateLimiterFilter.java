package com.shareride.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.ResponseLevel;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.ratelimit.RateLimitResult;
import com.shareride.gateway.service.GatewayMetricsService;
import com.shareride.gateway.service.RateLimiterService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise Reactive GlobalFilter enforcing Advanced Rate Limiting Strategies.
 *
 * <p>Enforces:
 *
 * <ul>
 *   <li>Tiered rate limiting: Anonymous (IP) vs Free authenticated users vs Premium users
 *   <li>Endpoint-specific limits: Fine-grained path/method rules (e.g. login/register brute-force
 *       protection)
 *   <li>Burst vs sustained limits: Distributed Token Bucket algorithm
 *   <li>Rate limit headers injection: {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining},
 *       {@code X-RateLimit-Reset}, and {@code Retry-After}
 *   <li>Standardized JSON HTTP 429 Too Many Requests response payload
 * </ul>
 */
@Slf4j
@Component
public class RateLimiterFilter implements GlobalFilter, Ordered {

    private final RateLimiterService rateLimiterService;
    private final GatewayMetricsService gatewayMetricsService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final ObjectMapper objectMapper;

    @Autowired
    public RateLimiterFilter(
            RateLimiterService rateLimiterService,
            GatewayMetricsService gatewayMetricsService,
            ObjectProvider<AuditLogService> auditLogServiceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.rateLimiterService = rateLimiterService;
        this.gatewayMetricsService = gatewayMetricsService;
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.objectMapper =
                (objectMapperProvider != null && objectMapperProvider.getIfAvailable() != null)
                        ? objectMapperProvider.getIfAvailable()
                        : new ObjectMapper();
    }

    public RateLimiterFilter(
            RateLimiterService rateLimiterService, GatewayMetricsService gatewayMetricsService) {
        this(rateLimiterService, gatewayMetricsService, null, null);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return rateLimiterService
                .evaluateRateLimit(exchange)
                .flatMap(
                        result -> {
                            ServerHttpResponse response = exchange.getResponse();

                            // Proactively attach standard rate limit headers
                            applyRateLimitHeaders(response.getHeaders(), result);

                            // Ensure headers are preserved before payload commitment
                            response.beforeCommit(
                                    () -> {
                                        applyRateLimitHeaders(response.getHeaders(), result);
                                        return Mono.empty();
                                    });

                            Route route =
                                    exchange.getAttribute(
                                            ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                            String routeId =
                                    (route != null && route.getId() != null)
                                            ? route.getId()
                                            : "unmatched";

                            if (result.allowed()) {
                                gatewayMetricsService.recordRateLimitAllowed(
                                        routeId, result.tier());
                                return chain.filter(exchange);
                            }

                            // 429 Too Many Requests
                            return writeRateLimitExceededResponse(exchange, result, routeId);
                        });
    }

    private void applyRateLimitHeaders(HttpHeaders headers, RateLimitResult result) {
        if (result.limit() > 0) {
            headers.set(GatewayConstants.HEADER_X_RATELIMIT_LIMIT, String.valueOf(result.limit()));
        }
        if (result.remaining() >= 0) {
            headers.set(
                    GatewayConstants.HEADER_X_RATELIMIT_REMAINING,
                    String.valueOf(result.remaining()));
        }
        if (result.resetSeconds() > 0) {
            headers.set(
                    GatewayConstants.HEADER_X_RATELIMIT_RESET,
                    String.valueOf(result.resetSeconds()));
        }
    }

    private Mono<Void> writeRateLimitExceededResponse(
            ServerWebExchange exchange, RateLimitResult result, String routeId) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        String clientIp = rateLimiterService.resolveClientIp(exchange.getRequest());
        gatewayMetricsService.recordRateLimitExceeded(routeId, clientIp, result.tier());

        String path = exchange.getRequest().getPath().value();
        String userId =
                exchange.getRequest().getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
        String correlationId =
                exchange.getRequest().getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);

        AuditLogService auditService =
                (auditLogServiceProvider != null) ? auditLogServiceProvider.getIfAvailable() : null;
        if (auditService != null) {
            auditService.logAuthorization(
                    userId != null ? userId : "anonymous",
                    "RATE_LIMIT_EXCEEDED",
                    path,
                    "GATEWAY_ACCESS",
                    Map.of(
                            "clientIp",
                            clientIp,
                            "tier",
                            result.tier(),
                            "limit",
                            String.valueOf(result.limit()),
                            "correlationId",
                            correlationId != null ? correlationId : "unknown"));
        }

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        long retryAfter = Math.max(1, result.retryAfterSeconds());
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.getHeaders().set(GatewayConstants.HEADER_X_RATELIMIT_REMAINING, "0");

        if (correlationId != null) {
            response.getHeaders().set(GatewayConstants.CORRELATION_ID_HEADER, correlationId);
        }

        BaseResponse<Void> baseResponse =
                BaseResponse.<Void>builder()
                        .level(ResponseLevel.ERROR)
                        .fromResponseCode(ResponseCode.TOO_MANY_REQUESTS)
                        .message("Rate limit exceeded. Please throttle your requests.")
                        .correlationId(correlationId)
                        .path(path)
                        .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(baseResponse);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize 429 response to JSON", e);
            bytes =
                    ("{\"success\":false,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit"
                                    + " exceeded. Please throttle your requests.\"}")
                            .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
