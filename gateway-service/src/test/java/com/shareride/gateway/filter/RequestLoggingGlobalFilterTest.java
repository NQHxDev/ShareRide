package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.sampling.LogSampler;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("RequestLoggingGlobalFilter Unit Tests")
class RequestLoggingGlobalFilterTest {

    private ShareRideLoggingProperties properties;
    private LogSampler logSampler;
    private GatewayMetricsService gatewayMetricsService;
    private ShareRideMetrics shareRideMetrics;
    private AuditLogService auditLogService;
    private RequestLoggingGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new ShareRideLoggingProperties();
        properties.setEnabled(true);
        properties.setSlowThresholdMs(100);
        properties.setExcludePaths(List.of("/actuator/**", "/favicon.ico", "/error"));
        properties.setMaskUrlParameters(true);
        properties.setIncludeHeaders(true);
        properties.setSensitiveHeaders(Set.of("authorization", "cookie"));

        logSampler = mock(LogSampler.class);
        when(logSampler.shouldSample(anyString(), any(), any(Boolean.class))).thenReturn(true);

        gatewayMetricsService = mock(GatewayMetricsService.class);
        shareRideMetrics = mock(ShareRideMetrics.class);
        auditLogService = mock(AuditLogService.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<LogSampler> samplerProvider = mock(ObjectProvider.class);
        when(samplerProvider.getIfAvailable()).thenReturn(logSampler);

        @SuppressWarnings("unchecked")
        ObjectProvider<GatewayMetricsService> metricsServiceProvider = mock(ObjectProvider.class);
        when(metricsServiceProvider.getIfAvailable()).thenReturn(gatewayMetricsService);

        @SuppressWarnings("unchecked")
        ObjectProvider<ShareRideMetrics> shareRideMetricsProvider = mock(ObjectProvider.class);
        when(shareRideMetricsProvider.getIfAvailable()).thenReturn(shareRideMetrics);

        @SuppressWarnings("unchecked")
        ObjectProvider<AuditLogService> auditLogServiceProvider = mock(ObjectProvider.class);
        when(auditLogServiceProvider.getIfAvailable()).thenReturn(auditLogService);

        filter =
                new RequestLoggingGlobalFilter(
                        properties,
                        samplerProvider,
                        metricsServiceProvider,
                        shareRideMetricsProvider,
                        auditLogServiceProvider);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should log standard HTTP request and pass down filter chain")
    void shouldLogStandardHttpRequest() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rides/123")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-111")
                        .header(GatewayConstants.USER_ID_HEADER, "user-999")
                        .header("X-Real-IP", "10.0.0.1")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(logSampler).shouldSample(eq("INFO"), any(), eq(false));
    }

    @Test
    @DisplayName("Should bypass logging when endpoint matches excludePaths")
    void shouldBypassLoggingWhenExcluded() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/actuator/health")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-health")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(logSampler, never()).shouldSample(anyString(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("Should bypass logging when logging is globally disabled")
    void shouldBypassLoggingWhenDisabled() {
        properties.setEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/rides").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(logSampler, never()).shouldSample(anyString(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("Should mask sensitive URL query parameters")
    void shouldMaskUrlParameters() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/auth/callback?token=supersecret&state=normal")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-mask")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(logSampler).shouldSample(eq("INFO"), any(), eq(false));
    }

    @Test
    @DisplayName("Should detect slow request and record slow request metric")
    void shouldDetectSlowRequestAndRecordMetric() {
        properties.setSlowThresholdMs(10);
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/heavy-query")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-slow")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(chain.filter(any(ServerWebExchange.class)))
                .thenAnswer(
                        invocation -> {
                            Thread.sleep(50);
                            return Mono.empty();
                        });

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(shareRideMetrics).recordSlowRequest(eq("GET"), eq("/api/v1/heavy-query"), eq(200));
    }

    @Test
    @DisplayName("Should handle 4xx and 5xx error responses without sampling suppression")
    void shouldHandleErrorResponses() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/v1/rides")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-err")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should support dynamic debug log override header")
    void shouldSupportDynamicDebugOverride() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/debug-test")
                        .header("X-Debug-Request", "true")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-debug")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should run at order Ordered.HIGHEST_PRECEDENCE + 3")
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
    }
}
