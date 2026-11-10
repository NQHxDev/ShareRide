package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.gateway.constant.GatewayConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("CorrelationIdGlobalFilter Unit Tests")
class CorrelationIdGlobalFilterTest {

    private CorrelationIdGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdGlobalFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should generate new UUIDv7 Correlation ID when header is missing")
    void shouldGenerateNewCorrelationIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange capturedExchange = captor.getValue();
        String requestCorrelationId =
                capturedExchange
                        .getRequest()
                        .getHeaders()
                        .getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        assertThat(requestCorrelationId).isNotNull().isNotBlank();

        String responseCorrelationId =
                exchange.getResponse()
                        .getHeaders()
                        .getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        assertThat(responseCorrelationId).isEqualTo(requestCorrelationId);
    }

    @Test
    @DisplayName("Should preserve existing Correlation ID when header is provided by client")
    void shouldPreserveExistingCorrelationId() {
        String existingId = "client-trace-12345";
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.CORRELATION_ID_HEADER, existingId)
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange capturedExchange = captor.getValue();
        String requestCorrelationId =
                capturedExchange
                        .getRequest()
                        .getHeaders()
                        .getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        assertThat(requestCorrelationId).isEqualTo(existingId);

        String responseCorrelationId =
                exchange.getResponse()
                        .getHeaders()
                        .getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        assertThat(responseCorrelationId).isEqualTo(existingId);
    }

    @Test
    @DisplayName("Should have highest precedence order")
    void shouldHaveHighestPrecedenceOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
