package com.shareride.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.gateway.constant.GatewayConstants;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

@DisplayName("RateLimiterConfig Unit Tests")
class RateLimiterConfigTest {

    private RateLimiterConfig config;
    private KeyResolver ipKeyResolver;
    private KeyResolver userKeyResolver;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
        ipKeyResolver = config.ipKeyResolver();
        userKeyResolver = config.userKeyResolver(ipKeyResolver);
    }

    @Test
    @DisplayName("Should resolve IP from X-Forwarded-For header")
    void shouldResolveIpFromXForwardedFor() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.FORWARDED_FOR_HEADER, "203.0.113.195, 70.41.3.18")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = ipKeyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Should resolve IP from X-Real-IP header")
    void shouldResolveIpFromXRealIp() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.REAL_IP_HEADER, "198.51.100.1")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = ipKeyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("198.51.100.1");
    }

    @Test
    @DisplayName("Should resolve IP from remote address when headers missing")
    void shouldResolveIpFromRemoteAddress() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = ipKeyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Should resolve User ID key when X-User-Id is provided")
    void shouldResolveUserIdKeyWhenProvided() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.USER_ID_HEADER, "user-uuid-12345")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = userKeyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("user:user-uuid-12345");
    }

    @Test
    @DisplayName("Should fall back to IP key when X-User-Id is missing")
    void shouldFallbackToIpKeyWhenUserMissing() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.REAL_IP_HEADER, "198.51.100.1")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = userKeyResolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:198.51.100.1");
    }
}
