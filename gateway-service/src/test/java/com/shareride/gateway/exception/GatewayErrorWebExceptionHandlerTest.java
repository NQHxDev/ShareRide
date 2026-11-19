package com.shareride.gateway.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("GatewayErrorWebExceptionHandler Unit Tests")
class GatewayErrorWebExceptionHandlerTest {

    private GatewayMetricsService gatewayMetricsService;
    private GatewayErrorWebExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        gatewayMetricsService = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        exceptionHandler = new GatewayErrorWebExceptionHandler(objectMapper, gatewayMetricsService);
    }

    @Test
    @DisplayName("Should format 404 NOT_FOUND into BaseResponse JSON and record error metric")
    void shouldFormat404IntoBaseResponseJson() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/unknown-path")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-404")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found");

        exceptionHandler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(
                        exchange.getResponse()
                                .getHeaders()
                                .getFirst(GatewayConstants.CORRELATION_ID_HEADER))
                .isEqualTo("corr-404");

        verify(gatewayMetricsService)
                .recordGatewayError("unmatched", "/api/v1/unknown-path", 404, "NOT_FOUND");

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("\"code\":\"NOT_FOUND\"");
        assertThat(body).contains("corr-404");
    }

    @Test
    @DisplayName(
            "Should format 429 TOO_MANY_REQUESTS into BaseResponse JSON and record rate limit"
                    + " metric")
    void shouldFormat429IntoBaseResponseJson() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rate-limited")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-429")
                        .header(GatewayConstants.REAL_IP_HEADER, "203.0.113.195")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");

        exceptionHandler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        verify(gatewayMetricsService)
                .recordGatewayError("unmatched", "/api/v1/rate-limited", 429, "TOO_MANY_REQUESTS");
        verify(gatewayMetricsService).recordRateLimitExceeded("unmatched", "203.0.113.195", "ip");

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("\"code\":\"TOO_MANY_REQUESTS\"");
    }

    @Test
    @DisplayName("Should map ConnectException to 503 SERVICE_UNAVAILABLE with clean BaseResponse")
    void shouldMapConnectExceptionTo503ServiceUnavailable() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/auth/v3/api-docs")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-connect")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        java.net.ConnectException ex =
                new java.net.ConnectException("Connection refused: localhost/127.0.0.1:8081");

        exceptionHandler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(gatewayMetricsService)
                .recordGatewayError(
                        "unmatched", "/api/v1/auth/v3/api-docs", 503, "SERVICE_UNAVAILABLE");

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(body).contains("Downstream service is temporarily unavailable");
    }

    @Test
    @DisplayName("Should map TimeoutException to 504 GATEWAY_TIMEOUT with clean BaseResponse")
    void shouldMapTimeoutExceptionTo504GatewayTimeout() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/templates/v3/api-docs")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, "corr-timeout")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        java.util.concurrent.TimeoutException ex =
                new java.util.concurrent.TimeoutException("Connection timed out after 3000ms");

        exceptionHandler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        verify(gatewayMetricsService)
                .recordGatewayError(
                        "unmatched", "/api/v1/templates/v3/api-docs", 504, "GATEWAY_TIMEOUT");

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":\"GATEWAY_TIMEOUT\"");
        assertThat(body).contains("Gateway Timeout");
    }
}
