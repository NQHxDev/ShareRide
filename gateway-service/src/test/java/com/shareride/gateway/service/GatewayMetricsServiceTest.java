package com.shareride.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.impl.GatewayMetricsServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@DisplayName("GatewayMetricsService Unit Tests")
class GatewayMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private ShareRideMetrics shareRideMetrics;
    private GatewayMetricsServiceImpl gatewayMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        shareRideMetrics = new ShareRideMetrics(meterRegistry);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("shareRideMetrics", shareRideMetrics);

        gatewayMetricsService =
                new GatewayMetricsServiceImpl(
                        meterRegistry, beanFactory.getBeanProvider(ShareRideMetrics.class));
    }

    @Nested
    @DisplayName("Active In-Flight Requests Tests")
    class ActiveRequestsTests {

        @Test
        @DisplayName("Should increment and decrement global and route active request counters")
        void shouldTrackActiveRequests() {
            assertThat(gatewayMetricsService.getActiveRequests()).isZero();
            assertThat(gatewayMetricsService.getActiveRequests("auth-service-route")).isZero();

            gatewayMetricsService.incrementActiveRequests("auth-service-route");
            assertThat(gatewayMetricsService.getActiveRequests()).isEqualTo(1);
            assertThat(gatewayMetricsService.getActiveRequests("auth-service-route")).isEqualTo(1);

            gatewayMetricsService.incrementActiveRequests("auth-service-route");
            assertThat(gatewayMetricsService.getActiveRequests()).isEqualTo(2);
            assertThat(gatewayMetricsService.getActiveRequests("auth-service-route")).isEqualTo(2);

            gatewayMetricsService.decrementActiveRequests("auth-service-route");
            assertThat(gatewayMetricsService.getActiveRequests()).isEqualTo(1);
            assertThat(gatewayMetricsService.getActiveRequests("auth-service-route")).isEqualTo(1);

            gatewayMetricsService.decrementActiveRequests("auth-service-route");
            assertThat(gatewayMetricsService.getActiveRequests()).isZero();
            assertThat(gatewayMetricsService.getActiveRequests("auth-service-route")).isZero();

            // Should never go below 0
            gatewayMetricsService.decrementActiveRequests("auth-service-route");
            assertThat(gatewayMetricsService.getActiveRequests()).isZero();
            assertThat(gatewayMetricsService.getActiveRequests("auth-service-route")).isZero();
        }
    }

    @Nested
    @DisplayName("Request Latency & Throughput Tests")
    class RequestMetricsTests {

        @Test
        @DisplayName("Should record 2xx successful request metrics")
        void shouldRecordSuccessfulRequest() {
            gatewayMetricsService.recordRequest(
                    "auth-service-route", "GET", "/api/v1/auth/me", 200, Duration.ofMillis(50));

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_REQUESTS_TOTAL)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(GatewayConstants.TAG_STATUS, "200")
                            .tag(GatewayConstants.TAG_STATUS_GROUP, "2xx")
                            .tag(GatewayConstants.TAG_OUTCOME, "SUCCESS")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);

            double durationCount =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_REQUESTS_DURATION)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(GatewayConstants.TAG_STATUS, "200")
                            .timer()
                            .count();
            assertThat(durationCount).isEqualTo(1L);

            // Fast request should NOT record slow request metric
            assertThat(
                            meterRegistry
                                    .find(
                                            "shareride."
                                                    + GatewayConstants.METRIC_GATEWAY_REQUESTS_SLOW)
                                    .counter())
                    .isNull();
        }

        @Test
        @DisplayName("Should record slow request when duration exceeds threshold")
        void shouldRecordSlowRequest() {
            gatewayMetricsService.recordRequest(
                    "auth-service-route",
                    "POST",
                    "/api/v1/auth/login",
                    200,
                    Duration.ofMillis(1500));

            double slowCount =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_REQUESTS_SLOW)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(GatewayConstants.TAG_METHOD, "POST")
                            .tag(GatewayConstants.TAG_STATUS, "200")
                            .counter()
                            .count();
            assertThat(slowCount).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should resolve correct status groups and outcomes for 3xx, 4xx, 5xx")
        void shouldResolveStatusGroups() {
            gatewayMetricsService.recordRequest(
                    "auth-service-route", "GET", "/api/v1/redirect", 302, Duration.ofMillis(10));
            gatewayMetricsService.recordRequest(
                    "auth-service-route", "GET", "/api/v1/not-found", 404, Duration.ofMillis(15));
            gatewayMetricsService.recordRequest(
                    "auth-service-route", "POST", "/api/v1/error", 500, Duration.ofMillis(20));

            assertThat(
                            meterRegistry
                                    .get(
                                            "shareride."
                                                    + GatewayConstants
                                                            .METRIC_GATEWAY_REQUESTS_TOTAL)
                                    .tag(GatewayConstants.TAG_STATUS, "302")
                                    .tag(GatewayConstants.TAG_OUTCOME, "REDIRECTION")
                                    .counter()
                                    .count())
                    .isEqualTo(1.0);

            assertThat(
                            meterRegistry
                                    .get(
                                            "shareride."
                                                    + GatewayConstants
                                                            .METRIC_GATEWAY_REQUESTS_TOTAL)
                                    .tag(GatewayConstants.TAG_STATUS, "404")
                                    .tag(GatewayConstants.TAG_OUTCOME, "CLIENT_ERROR")
                                    .counter()
                                    .count())
                    .isEqualTo(1.0);

            assertThat(
                            meterRegistry
                                    .get(
                                            "shareride."
                                                    + GatewayConstants
                                                            .METRIC_GATEWAY_REQUESTS_TOTAL)
                                    .tag(GatewayConstants.TAG_STATUS, "500")
                                    .tag(GatewayConstants.TAG_OUTCOME, "SERVER_ERROR")
                                    .counter()
                                    .count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should sanitize null values safely")
        void shouldSanitizeNullValues() {
            gatewayMetricsService.recordRequest(null, null, null, 0, null);

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_REQUESTS_TOTAL)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "unmatched")
                            .tag(GatewayConstants.TAG_METHOD, "UNKNOWN")
                            .tag(GatewayConstants.TAG_STATUS_GROUP, "unknown")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Rate Limiting Metrics Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Should record rate limit rejections and sanitize IP prefixes")
        void shouldRecordRateLimitRejection() {
            gatewayMetricsService.recordRateLimitExceeded(
                    "auth-service-route", "192.168.1.45", "ip");

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_RATELIMIT_REJECTED)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(GatewayConstants.TAG_LIMIT_TYPE, "ip")
                            .tag(GatewayConstants.TAG_CLIENT_IP_PREFIX, "192.168.1.0/24")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);

            // Also check security alert metric
            double securityCount =
                    meterRegistry
                            .get("shareride.security.ratelimit.exceeded")
                            .tag("endpoint", "auth-service-route")
                            .tag("client_ip_prefix", "192.168.1.0/24")
                            .counter()
                            .count();
            assertThat(securityCount).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should record rate limit allowed metric")
        void shouldRecordRateLimitAllowed() {
            gatewayMetricsService.recordRateLimitAllowed("auth-service-route", "ip");

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_RATELIMIT_ALLOWED)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(GatewayConstants.TAG_LIMIT_TYPE, "ip")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Resilience & Fallback Metrics Tests")
    class ResilienceMetricsTests {

        @Test
        @DisplayName("Should record circuit breaker fallback executions")
        void shouldRecordCircuitBreakerFallback() {
            gatewayMetricsService.recordCircuitBreakerFallback(
                    "auth-service-route",
                    GatewayConstants.AUTH_CIRCUIT_BREAKER,
                    "service_unavailable");

            double count =
                    meterRegistry
                            .get(
                                    "shareride."
                                            + GatewayConstants
                                                    .METRIC_GATEWAY_CIRCUITBREAKER_FALLBACK)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(
                                    GatewayConstants.TAG_CIRCUIT_BREAKER,
                                    GatewayConstants.AUTH_CIRCUIT_BREAKER)
                            .tag(GatewayConstants.TAG_REASON, "service_unavailable")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);

            // Check shared fallback counter
            double fallbackCount =
                    meterRegistry
                            .get("shareride.resilience.fallback.total")
                            .tag("operation", GatewayConstants.AUTH_CIRCUIT_BREAKER)
                            .tag("reason", "service_unavailable")
                            .counter()
                            .count();
            assertThat(fallbackCount).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Perimeter Authentication Metrics Tests")
    class AuthenticationMetricsTests {

        @Test
        @DisplayName("Should record public endpoint bypass events")
        void shouldRecordPublicBypass() {
            gatewayMetricsService.recordAuthEvent(
                    "PUBLIC_BYPASS", "/api/v1/auth/login", "whitelisted");

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_AUTH_EVENTS)
                            .tag(GatewayConstants.TAG_EVENT_TYPE, "PUBLIC_BYPASS")
                            .tag(GatewayConstants.TAG_PATH, "/api/v1/auth/login")
                            .tag(GatewayConstants.TAG_REASON, "whitelisted")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should record missing token events and increment auth failure metric")
        void shouldRecordMissingToken() {
            gatewayMetricsService.recordAuthEvent("MISSING_TOKEN", "/api/v1/rides", "no_token");

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_AUTH_EVENTS)
                            .tag(GatewayConstants.TAG_EVENT_TYPE, "MISSING_TOKEN")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);

            double failureCount =
                    meterRegistry
                            .get("shareride.security.auth.failure")
                            .tag("auth_type", "jwt")
                            .tag("reason", "no_token")
                            .counter()
                            .count();
            assertThat(failureCount).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Gateway Error Metrics Tests")
    class GatewayErrorMetricsTests {

        @Test
        @DisplayName("Should record gateway-level errors with status and error codes")
        void shouldRecordGatewayError() {
            gatewayMetricsService.recordGatewayError(
                    "auth-service-route", "/api/v1/auth/test", 503, "SERVICE_UNAVAILABLE");

            double count =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_ERRORS_TOTAL)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .tag(GatewayConstants.TAG_STATUS, "503")
                            .tag(GatewayConstants.TAG_ERROR_CODE, "SERVICE_UNAVAILABLE")
                            .counter()
                            .count();
            assertThat(count).isEqualTo(1.0);

            double errorCount =
                    meterRegistry
                            .get("shareride.errors.total")
                            .tag("code", "SERVICE_UNAVAILABLE")
                            .counter()
                            .count();
            assertThat(errorCount).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Payload Size Distribution Tests")
    class PayloadSizeTests {

        @Test
        @DisplayName("Should record request and response distribution summaries")
        void shouldRecordPayloadSizes() {
            gatewayMetricsService.recordRequestBodySize("auth-service-route", 1024L);
            gatewayMetricsService.recordResponseBodySize("auth-service-route", 2048L);

            double reqTotal =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_REQUEST_SIZE)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .summary()
                            .totalAmount();
            assertThat(reqTotal).isEqualTo(1024.0);

            double respTotal =
                    meterRegistry
                            .get("shareride." + GatewayConstants.METRIC_GATEWAY_RESPONSE_SIZE)
                            .tag(GatewayConstants.TAG_ROUTE_ID, "auth-service-route")
                            .summary()
                            .totalAmount();
            assertThat(respTotal).isEqualTo(2048.0);
        }

        @Test
        @DisplayName("Should ignore zero or negative payload sizes")
        void shouldIgnoreZeroSizes() {
            gatewayMetricsService.recordRequestBodySize("auth-service-route", 0L);
            gatewayMetricsService.recordResponseBodySize("auth-service-route", -10L);

            assertThat(
                            meterRegistry
                                    .find(
                                            "shareride."
                                                    + GatewayConstants.METRIC_GATEWAY_REQUEST_SIZE)
                                    .summary())
                    .isNull();
            assertThat(
                            meterRegistry
                                    .find(
                                            "shareride."
                                                    + GatewayConstants.METRIC_GATEWAY_RESPONSE_SIZE)
                                    .summary())
                    .isNull();
        }
    }
}
