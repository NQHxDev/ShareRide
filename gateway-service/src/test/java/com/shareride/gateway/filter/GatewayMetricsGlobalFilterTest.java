package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.gateway.service.GatewayMetricsService;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayMetricsGlobalFilter Unit Tests")
class GatewayMetricsGlobalFilterTest {

    @Mock private GatewayMetricsService gatewayMetricsService;
    @Mock private GatewayFilterChain filterChain;

    private GatewayMetricsGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayMetricsGlobalFilter(gatewayMetricsService);
    }

    @Test
    @DisplayName("Should measure and record metrics for routed request with payload sizes")
    void shouldMeasureAndRecordMetricsForRoutedRequest() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header(HttpHeaders.CONTENT_LENGTH, "128")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentLength(512);

        Route route =
                Route.async()
                        .id("auth-service-route")
                        .uri(URI.create("http://localhost:8081"))
                        .order(0)
                        .predicate(swe -> true)
                        .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        verify(gatewayMetricsService).incrementActiveRequests("auth-service-route");
        verify(gatewayMetricsService).recordRequestBodySize("auth-service-route", 128L);
        verify(gatewayMetricsService).decrementActiveRequests("auth-service-route");
        verify(gatewayMetricsService)
                .recordRequest(
                        eq("auth-service-route"),
                        eq("POST"),
                        eq("/api/v1/auth/login"),
                        eq(200),
                        any(Duration.class));
        verify(gatewayMetricsService).recordResponseBodySize("auth-service-route", 512L);
    }

    @Test
    @DisplayName("Should fallback to 'unmatched' when no route is matched")
    void shouldHandleUnmatchedRoute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/unmatched/path").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);

        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        verify(gatewayMetricsService).incrementActiveRequests("unmatched");
        verify(gatewayMetricsService).decrementActiveRequests("unmatched");
        verify(gatewayMetricsService)
                .recordRequest(
                        eq("unmatched"),
                        eq("GET"),
                        eq("/unmatched/path"),
                        eq(404),
                        any(Duration.class));
    }

    @Test
    @DisplayName("Should execute doFinally cleanup and metrics even if downstream chain errors")
    void shouldRecordMetricsOnChainError() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/error").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        when(filterChain.filter(exchange))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyError(RuntimeException.class);

        verify(gatewayMetricsService).incrementActiveRequests("unmatched");
        verify(gatewayMetricsService).decrementActiveRequests("unmatched");
        verify(gatewayMetricsService)
                .recordRequest(
                        eq("unmatched"),
                        eq("GET"),
                        eq("/api/v1/error"),
                        eq(500),
                        any(Duration.class));
    }

    @Test
    @DisplayName("Filter order should be Ordered.HIGHEST_PRECEDENCE + 1")
    void shouldHaveExpectedFilterOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }
}
