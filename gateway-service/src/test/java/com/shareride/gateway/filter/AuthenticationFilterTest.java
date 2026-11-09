package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.gateway.service.GatewayMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("AuthenticationFilter Unit Tests")
class AuthenticationFilterTest {

    private GatewayMetricsService gatewayMetricsService;
    private AuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        gatewayMetricsService = mock(GatewayMetricsService.class);
        filter = new AuthenticationFilter(gatewayMetricsService);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName(
            "Should pass through public login endpoint without authentication and record bypass"
                    + " metric")
    void shouldPassThroughPublicLoginEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/api/v1/auth/login", "whitelisted");
    }

    @Test
    @DisplayName(
            "Should pass through public register endpoint without authentication and record bypass"
                    + " metric")
    void shouldPassThroughPublicRegisterEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/api/v1/auth/register", "whitelisted");
    }

    @Test
    @DisplayName("Should pass through actuator health endpoints and record bypass metric")
    void shouldPassThroughActuatorHealthEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("PUBLIC_BYPASS", "/actuator/health", "whitelisted");
    }

    @Test
    @DisplayName("Should record token present metric for protected endpoint with Bearer header")
    void shouldPassThroughProtectedEndpointWithBearerToken() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rides/current")
                        .header("Authorization", "Bearer mock-token-12345")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("TOKEN_PRESENT", "/api/v1/rides/current", "bearer");
    }

    @Test
    @DisplayName("Should record missing token metric for protected endpoint without Bearer header")
    void shouldRecordMissingTokenForProtectedEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/rides/current").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(gatewayMetricsService)
                .recordAuthEvent("MISSING_TOKEN", "/api/v1/rides/current", "no_bearer_token");
    }

    @Test
    @DisplayName("Should run after GatewayMetricsGlobalFilter (Ordered.HIGHEST_PRECEDENCE + 2)")
    void shouldRunAfterMetricsGlobalFilter() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
    }
}
