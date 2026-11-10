package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.ratelimit.RateLimitResult;
import com.shareride.gateway.service.GatewayMetricsService;
import com.shareride.gateway.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("RateLimiterFilter Unit Tests")
class RateLimiterFilterTest {

    private RateLimiterService rateLimiterService;
    private GatewayMetricsService metricsService;
    private AuditLogService auditLogService;
    private RateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiterService = mock(RateLimiterService.class);
        metricsService = mock(GatewayMetricsService.class);
        auditLogService = mock(AuditLogService.class);

        filter =
                new RateLimiterFilter(
                        rateLimiterService,
                        metricsService,
                        mockProvider(auditLogService),
                        mockProvider(new ObjectMapper()));
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> mockProvider(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    @Test
    @DisplayName("Should verify filter ordering is after SecurityHeadersFilter")
    void shouldVerifyFilterOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 5);
    }

    @Test
    @DisplayName("Should allow request, inject rate limit headers, and proceed with chain")
    void shouldAllowRequestAndInjectHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitResult result =
                RateLimitResult.builder()
                        .allowed(true)
                        .limit(60)
                        .replenishRate(30)
                        .remaining(59)
                        .resetSeconds(1762777200)
                        .retryAfterSeconds(0)
                        .tier("free")
                        .key("rl:tier:free:u:test")
                        .build();

        when(rateLimiterService.evaluateRateLimit(exchange)).thenReturn(Mono.just(result));

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(metricsService).recordRateLimitAllowed("unmatched", "free");

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_RATELIMIT_LIMIT)).isEqualTo("60");
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_RATELIMIT_REMAINING)).isEqualTo("59");
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_RATELIMIT_RESET))
                .isEqualTo("1762777200");
    }

    @Test
    @DisplayName("Should reject request with 429 when rate limit exceeded")
    void shouldRejectRequestWith429WhenLimitExceeded() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-12345")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitResult result =
                RateLimitResult.builder()
                        .allowed(false)
                        .limit(10)
                        .replenishRate(5)
                        .remaining(0)
                        .resetSeconds(1762777250)
                        .retryAfterSeconds(3)
                        .tier("endpoint:/api/v1/auth/login")
                        .key("rl:ep:/api/v1/auth/login:ip:10.0.0.1")
                        .build();

        when(rateLimiterService.evaluateRateLimit(exchange)).thenReturn(Mono.just(result));
        when(rateLimiterService.resolveClientIp(request)).thenReturn("10.0.0.1");

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // Chain must NOT be called
        verifyNoInteractions(chain);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_RATELIMIT_LIMIT)).isEqualTo("10");
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_RATELIMIT_REMAINING)).isEqualTo("0");
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_RATELIMIT_RESET))
                .isEqualTo("1762777250");
        assertThat(headers.getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("3");
        assertThat(headers.getFirst(GatewayConstants.CORRELATION_ID_HEADER))
                .isEqualTo("corr-12345");

        verify(metricsService)
                .recordRateLimitExceeded("unmatched", "10.0.0.1", "endpoint:/api/v1/auth/login");
        verify(auditLogService)
                .logAuthorization(
                        eq("anonymous"),
                        eq("RATE_LIMIT_EXCEEDED"),
                        eq("/api/v1/auth/login"),
                        eq("GATEWAY_ACCESS"),
                        any());
    }
}
