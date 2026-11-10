package com.shareride.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.ResponseLevel;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global reactive exception handler converting all Gateway-level failures (404, 429, 502, 503, 504)
 * into standardized {@link BaseResponse} JSON payloads, recording security audit events and
 * metrics.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final GatewayMetricsService gatewayMetricsService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;

    @Autowired
    public GatewayErrorWebExceptionHandler(
            ObjectMapper objectMapper,
            GatewayMetricsService gatewayMetricsService,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.objectMapper = objectMapper;
        this.gatewayMetricsService = gatewayMetricsService;
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    public GatewayErrorWebExceptionHandler(
            ObjectMapper objectMapper, GatewayMetricsService gatewayMetricsService) {
        this(objectMapper, gatewayMetricsService, null);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String correlationId =
                exchange.getRequest().getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_HEADER);
        }
        String path = exchange.getRequest().getPath().value();

        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        ResponseCode responseCode = ResponseCode.INTERNAL_SERVER_ERROR;
        String errorMessage = "An unexpected error occurred in API Gateway";

        if (ex instanceof ResponseStatusException rse) {
            status = rse.getStatusCode();
            if (status.equals(HttpStatus.NOT_FOUND)) {
                responseCode = ResponseCode.NOT_FOUND;
                errorMessage = "The requested resource or endpoint was not found";
            } else if (status.equals(HttpStatus.TOO_MANY_REQUESTS)) {
                responseCode = ResponseCode.TOO_MANY_REQUESTS;
                errorMessage = "Rate limit exceeded. Please throttle your requests.";
            } else if (status.equals(HttpStatus.BAD_GATEWAY)) {
                responseCode = ResponseCode.BAD_GATEWAY;
                errorMessage = "Bad Gateway. Downstream service returned an invalid response.";
            } else if (status.equals(HttpStatus.GATEWAY_TIMEOUT)) {
                responseCode = ResponseCode.GATEWAY_TIMEOUT;
                errorMessage = "Gateway Timeout. Downstream service took too long to respond.";
            } else if (status.equals(HttpStatus.SERVICE_UNAVAILABLE)) {
                responseCode = ResponseCode.SERVICE_UNAVAILABLE;
                errorMessage = "Service is temporarily unavailable. Please retry later.";
            } else if (status.equals(HttpStatus.UNAUTHORIZED)) {
                responseCode = ResponseCode.UNAUTHORIZED;
                errorMessage = "Authentication credentials are missing or invalid";
            } else {
                errorMessage = rse.getReason() != null ? rse.getReason() : errorMessage;
            }
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "unmatched";

        gatewayMetricsService.recordGatewayError(
                routeId, path, status.value(), responseCode.name());

        String clientIp = resolveClientIp(exchange.getRequest());
        AuditLogService auditService =
                (auditLogServiceProvider != null) ? auditLogServiceProvider.getIfAvailable() : null;

        if (status.equals(HttpStatus.TOO_MANY_REQUESTS)) {
            gatewayMetricsService.recordRateLimitExceeded(routeId, clientIp, "ip");
            if (auditService != null) {
                auditService.logAuthorization(
                        "anonymous",
                        "RATE_LIMIT_EXCEEDED",
                        path,
                        "GATEWAY_ACCESS",
                        Map.of(
                                "clientIp",
                                clientIp,
                                "correlationId",
                                correlationId != null ? correlationId : "unknown"));
            }
        } else if (status.equals(HttpStatus.UNAUTHORIZED) || status.equals(HttpStatus.FORBIDDEN)) {
            if (auditService != null) {
                auditService.logAuthorization(
                        "anonymous",
                        responseCode.name(),
                        path,
                        "GATEWAY_ACCESS",
                        Map.of(
                                "clientIp",
                                clientIp,
                                "correlationId",
                                correlationId != null ? correlationId : "unknown"));
            }
        }

        try {
            if (correlationId != null) {
                MDC.put(MdcConstants.CORRELATION_ID, correlationId);
            }
            MDC.put(MdcConstants.HTTP_URI, path);
            MDC.put(MdcConstants.SERVICE_NAME, "gateway-service");
            MDC.put(MdcConstants.CLIENT_IP, clientIp);
            log.error(
                    "Gateway exception occurred: path=[{}] status=[{}] message=[{}]"
                            + " correlationId=[{}]",
                    path,
                    status.value(),
                    ex.getMessage(),
                    correlationId,
                    ex);
        } finally {
            MDC.remove(MdcConstants.CORRELATION_ID);
            MDC.remove(MdcConstants.HTTP_URI);
            MDC.remove(MdcConstants.SERVICE_NAME);
            MDC.remove(MdcConstants.CLIENT_IP);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            response.getHeaders().set(GatewayConstants.CORRELATION_ID_HEADER, correlationId);
        }

        BaseResponse<Void> baseResponse =
                BaseResponse.<Void>builder()
                        .level(ResponseLevel.ERROR)
                        .fromResponseCode(responseCode)
                        .message(errorMessage)
                        .correlationId(correlationId)
                        .path(path)
                        .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(baseResponse);
        } catch (JsonProcessingException jsonException) {
            log.error("Failed to serialize gateway error response to JSON", jsonException);
            bytes =
                    "{\"success\":false,\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Internal Error\"}"
                            .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
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
}
