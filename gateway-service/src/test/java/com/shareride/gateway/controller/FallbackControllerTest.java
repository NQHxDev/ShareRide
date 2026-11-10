package com.shareride.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.response.BaseResponse;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

@DisplayName("FallbackController Unit Tests")
class FallbackControllerTest {

    private GatewayMetricsService gatewayMetricsService;
    private FallbackController controller;

    @BeforeEach
    void setUp() {
        gatewayMetricsService = mock(GatewayMetricsService.class);
        controller = new FallbackController(gatewayMetricsService);
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable on Auth Fallback and record metric")
    void shouldReturn503OnAuthFallback() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/fallback/auth")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-12345")
                        .build();

        ResponseEntity<BaseResponse<Void>> responseEntity =
                controller.authFallback(request).block();

        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER))
                .isEqualTo("corr-12345");

        verify(gatewayMetricsService)
                .recordCircuitBreakerFallback(
                        "auth-service-route",
                        GatewayConstants.AUTH_CIRCUIT_BREAKER,
                        "circuit_breaker_open_or_timeout");

        BaseResponse<Void> body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo(ResponseCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(body.getCorrelationId()).isEqualTo("corr-12345");
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable on Default Fallback and record metric")
    void shouldReturn503OnDefaultFallback() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/fallback/default")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-67890")
                        .build();

        ResponseEntity<BaseResponse<Void>> responseEntity =
                controller.defaultFallback(request).block();

        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verify(gatewayMetricsService)
                .recordCircuitBreakerFallback(
                        "default-route",
                        GatewayConstants.DEFAULT_CIRCUIT_BREAKER,
                        "circuit_breaker_open_or_timeout");

        BaseResponse<Void> body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo(ResponseCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(body.getCorrelationId()).isEqualTo("corr-67890");
    }
}
