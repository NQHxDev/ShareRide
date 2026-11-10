package com.shareride.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.gateway.config.SecurityHeadersConfig;
import com.shareride.gateway.constant.GatewayConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("SecurityHeadersFilter Unit Tests")
class SecurityHeadersFilterTest {

    private SecurityHeadersConfig config;
    private SecurityHeadersFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        config = new SecurityHeadersConfig();
        filter = new SecurityHeadersFilter(config);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should inject default enterprise security headers when enabled")
    void shouldInjectDefaultSecurityHeadersWhenEnabled() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();

        assertThat(headers.getFirst(GatewayConstants.HEADER_X_CONTENT_TYPE_OPTIONS))
                .isEqualTo(GatewayConstants.DEFAULT_X_CONTENT_TYPE_OPTIONS);
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_FRAME_OPTIONS))
                .isEqualTo(GatewayConstants.DEFAULT_X_FRAME_OPTIONS);
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_XSS_PROTECTION))
                .isEqualTo(GatewayConstants.DEFAULT_X_XSS_PROTECTION);
        assertThat(headers.getFirst(GatewayConstants.HEADER_STRICT_TRANSPORT_SECURITY))
                .isEqualTo(GatewayConstants.DEFAULT_STRICT_TRANSPORT_SECURITY);
        assertThat(headers.getFirst(GatewayConstants.HEADER_CONTENT_SECURITY_POLICY))
                .isEqualTo(GatewayConstants.DEFAULT_CONTENT_SECURITY_POLICY);
        assertThat(headers.getFirst(GatewayConstants.HEADER_REFERRER_POLICY))
                .isEqualTo(GatewayConstants.DEFAULT_REFERRER_POLICY);
        assertThat(headers.getFirst(GatewayConstants.HEADER_PERMISSIONS_POLICY))
                .isEqualTo(GatewayConstants.DEFAULT_PERMISSIONS_POLICY);
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_PERMITTED_CROSS_DOMAIN_POLICIES))
                .isEqualTo(GatewayConstants.DEFAULT_X_PERMITTED_CROSS_DOMAIN_POLICIES);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
    }

    @Test
    @DisplayName("Should bypass header injection when disabled")
    void shouldBypassWhenDisabled() {
        config.setEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_CONTENT_TYPE_OPTIONS)).isNull();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_FRAME_OPTIONS)).isNull();
        assertThat(headers.getFirst(GatewayConstants.HEADER_STRICT_TRANSPORT_SECURITY)).isNull();
        assertThat(headers.getFirst(GatewayConstants.HEADER_CONTENT_SECURITY_POLICY)).isNull();
    }

    @Test
    @DisplayName("Should preserve existing downstream headers when overrideExisting is false")
    void shouldPreserveExistingHeadersWhenOverrideIsFalse() {
        config.setOverrideExisting(false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse()
                .getHeaders()
                .set(GatewayConstants.HEADER_X_FRAME_OPTIONS, "SAMEORIGIN");
        exchange.getResponse()
                .getHeaders()
                .set(GatewayConstants.HEADER_CONTENT_SECURITY_POLICY, "default-src 'none'");

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_FRAME_OPTIONS))
                .isEqualTo("SAMEORIGIN");
        assertThat(headers.getFirst(GatewayConstants.HEADER_CONTENT_SECURITY_POLICY))
                .isEqualTo("default-src 'none'");
        // Non-existing headers should still be populated
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_CONTENT_TYPE_OPTIONS))
                .isEqualTo(GatewayConstants.DEFAULT_X_CONTENT_TYPE_OPTIONS);
    }

    @Test
    @DisplayName("Should overwrite existing downstream headers when overrideExisting is true")
    void shouldOverwriteExistingHeadersWhenOverrideIsTrue() {
        config.setOverrideExisting(true);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse()
                .getHeaders()
                .set(GatewayConstants.HEADER_X_FRAME_OPTIONS, "SAMEORIGIN");

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_FRAME_OPTIONS))
                .isEqualTo(GatewayConstants.DEFAULT_X_FRAME_OPTIONS);
    }

    @Test
    @DisplayName("Should inject optional Cache-Control, Pragma, and Expires when configured")
    void shouldInjectOptionalCacheHeadersWhenConfigured() {
        config.setCacheControl("no-cache, no-store, max-age=0, must-revalidate");
        config.setPragma("no-cache");
        config.setExpires("0");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/secure/data").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        assertThat(headers.getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        assertThat(headers.getFirst(HttpHeaders.EXPIRES)).isEqualTo("0");
    }

    @Test
    @DisplayName("Should apply headers via beforeCommit callback on response commitment")
    void shouldApplyHeadersOnBeforeCommit() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(org.mockito.ArgumentMatchers.any(ServerWebExchange.class)))
                .thenAnswer(
                        invocation -> {
                            ServerWebExchange ex = invocation.getArgument(0);
                            // Clears headers downstream to simulate downstream proxying
                            ex.getResponse().getHeaders().clear();
                            return ex.getResponse().setComplete();
                        });

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_CONTENT_TYPE_OPTIONS))
                .isEqualTo(GatewayConstants.DEFAULT_X_CONTENT_TYPE_OPTIONS);
        assertThat(headers.getFirst(GatewayConstants.HEADER_X_FRAME_OPTIONS))
                .isEqualTo(GatewayConstants.DEFAULT_X_FRAME_OPTIONS);
        assertThat(headers.getFirst(GatewayConstants.HEADER_STRICT_TRANSPORT_SECURITY))
                .isEqualTo(GatewayConstants.DEFAULT_STRICT_TRANSPORT_SECURITY);
    }

    @Test
    @DisplayName("Should have correct filter order relative to gateway pipeline")
    void shouldHaveCorrectFilterOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 4);
    }
}
