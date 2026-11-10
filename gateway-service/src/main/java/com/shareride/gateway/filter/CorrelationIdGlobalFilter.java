package com.shareride.gateway.filter;

import com.github.f4b6a3.uuid.UuidCreator;
import com.shareride.common.logging.MdcConstants;
import com.shareride.gateway.constant.GatewayConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global reactive filter ensuring every incoming request carries a unique, time-ordered UUIDv7
 * Correlation ID.
 *
 * <p>Propagates the correlation ID to downstream microservices via request header, populates
 * exchange attributes and Reactor context for reactive pipelines, and returns it to the client in
 * the HTTP response headers.
 */
@Slf4j
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId =
                request.getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UuidCreator.getTimeOrderedEpoch().toString();
            try {
                MDC.put(MdcConstants.CORRELATION_ID, correlationId);
                log.debug(
                        "Generated new Correlation-ID [{}] for request [{}]",
                        correlationId,
                        request.getPath());
            } finally {
                MDC.remove(MdcConstants.CORRELATION_ID);
            }
        }

        // Store in exchange attributes for subsequent filters and error handlers
        exchange.getAttributes().put(GatewayConstants.CORRELATION_ID_HEADER, correlationId);

        // Add correlation ID to outgoing response header
        exchange.getResponse()
                .getHeaders()
                .set(GatewayConstants.CORRELATION_ID_HEADER, correlationId);

        // Mutate incoming request with correlation ID header for downstream microservices
        ServerHttpRequest mutatedRequest =
                request.mutate()
                        .header(GatewayConstants.CORRELATION_ID_HEADER, correlationId)
                        .build();

        final String finalCorrelationId = correlationId;
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(
                        context ->
                                context.put(
                                        GatewayConstants.CORRELATION_ID_HEADER,
                                        finalCorrelationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
