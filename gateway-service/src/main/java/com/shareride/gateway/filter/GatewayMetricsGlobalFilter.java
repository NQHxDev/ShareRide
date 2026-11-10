package com.shareride.gateway.filter;

import com.shareride.gateway.service.GatewayMetricsService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global reactive filter collecting latency timers, throughput counters, active request gauges, and
 * payload size distributions across API Gateway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayMetricsGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayMetricsService gatewayMetricsService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getPath().value();

        Route initialRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String initialRouteId = resolveRouteId(initialRoute);

        gatewayMetricsService.incrementActiveRequests(initialRouteId);

        // Record incoming payload size if Content-Length header is present
        long requestContentLength = request.getHeaders().getContentLength();
        if (requestContentLength > 0) {
            gatewayMetricsService.recordRequestBodySize(initialRouteId, requestContentLength);
        }

        return chain.filter(exchange)
                .doFinally(
                        signalType -> {
                            long durationNanos = System.nanoTime() - startNanos;
                            Duration duration = Duration.ofNanos(durationNanos);

                            Route finalRoute =
                                    exchange.getAttribute(
                                            ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                            String routeId =
                                    resolveRouteId(finalRoute != null ? finalRoute : initialRoute);

                            gatewayMetricsService.decrementActiveRequests(routeId);

                            HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                            int statusValue = (statusCode != null) ? statusCode.value() : 500;

                            gatewayMetricsService.recordRequest(
                                    routeId, method, path, statusValue, duration);

                            long responseContentLength =
                                    exchange.getResponse().getHeaders().getContentLength();
                            if (responseContentLength > 0) {
                                gatewayMetricsService.recordResponseBodySize(
                                        routeId, responseContentLength);
                            }
                        });
    }

    private String resolveRouteId(Route route) {
        if (route != null && route.getId() != null && !route.getId().isBlank()) {
            return route.getId();
        }
        return "unmatched";
    }

    @Override
    public int getOrder() {
        // Runs immediately after CorrelationIdGlobalFilter (Ordered.HIGHEST_PRECEDENCE)
        // so it wraps Authentication, Routing, RateLimiting, and Error handling
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
