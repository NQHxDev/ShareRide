package com.shareride.gateway.controller;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.ResponseLevel;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Fallback endpoints invoked by Spring Cloud Gateway Circuit Breakers when downstream services are
 * unavailable, unhealthy, or timing out.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
@RequiredArgsConstructor
public class FallbackController {

    private final GatewayMetricsService gatewayMetricsService;

    /** Fallback endpoint for Auth Service circuit breaker. */
    @RequestMapping("/auth")
    public Mono<ResponseEntity<BaseResponse<Void>>> authFallback(ServerHttpRequest request) {
        String correlationId =
                request.getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);

        try {
            if (correlationId != null) {
                MDC.put(MdcConstants.CORRELATION_ID, correlationId);
            }
            MDC.put(MdcConstants.SERVICE_NAME, "gateway-service");
            MDC.put(MdcConstants.HTTP_URI, request.getPath().value());
            log.warn(
                    "Auth Service circuit breaker fallback activated. Correlation-ID: [{}]",
                    correlationId);
        } finally {
            MDC.remove(MdcConstants.CORRELATION_ID);
            MDC.remove(MdcConstants.SERVICE_NAME);
            MDC.remove(MdcConstants.HTTP_URI);
        }

        gatewayMetricsService.recordCircuitBreakerFallback(
                "auth-service-route",
                GatewayConstants.AUTH_CIRCUIT_BREAKER,
                "circuit_breaker_open_or_timeout");

        BaseResponse<Void> response =
                BaseResponse.<Void>builder()
                        .level(ResponseLevel.ERROR)
                        .fromResponseCode(ResponseCode.SERVICE_UNAVAILABLE)
                        .message(
                                "Auth Service is temporarily unavailable or experiencing high"
                                        + " latency. Please retry shortly.")
                        .correlationId(correlationId)
                        .path(request.getPath().value())
                        .build();

        return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header(GatewayConstants.CORRELATION_ID_HEADER, correlationId)
                        .body(response));
    }

    /** Generic default fallback endpoint for general microservice circuit breakers. */
    @RequestMapping("/default")
    public Mono<ResponseEntity<BaseResponse<Void>>> defaultFallback(ServerHttpRequest request) {
        String correlationId =
                request.getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);

        try {
            if (correlationId != null) {
                MDC.put(MdcConstants.CORRELATION_ID, correlationId);
            }
            MDC.put(MdcConstants.SERVICE_NAME, "gateway-service");
            MDC.put(MdcConstants.HTTP_URI, request.getPath().value());
            log.warn(
                    "Default service circuit breaker fallback activated. Correlation-ID: [{}]",
                    correlationId);
        } finally {
            MDC.remove(MdcConstants.CORRELATION_ID);
            MDC.remove(MdcConstants.SERVICE_NAME);
            MDC.remove(MdcConstants.HTTP_URI);
        }

        gatewayMetricsService.recordCircuitBreakerFallback(
                "default-route",
                GatewayConstants.DEFAULT_CIRCUIT_BREAKER,
                "circuit_breaker_open_or_timeout");

        BaseResponse<Void> response =
                BaseResponse.<Void>builder()
                        .level(ResponseLevel.ERROR)
                        .fromResponseCode(ResponseCode.SERVICE_UNAVAILABLE)
                        .message(
                                "Target service is temporarily unavailable or experiencing high"
                                        + " latency. Please retry shortly.")
                        .correlationId(correlationId)
                        .path(request.getPath().value())
                        .build();

        return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header(GatewayConstants.CORRELATION_ID_HEADER, correlationId)
                        .body(response));
    }
}
