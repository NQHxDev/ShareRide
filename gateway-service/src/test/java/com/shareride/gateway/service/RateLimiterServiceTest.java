package com.shareride.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareride.gateway.config.RateLimiterConfig;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.ratelimit.EndpointRateLimitRule;
import com.shareride.gateway.ratelimit.RateLimitResult;
import com.shareride.gateway.ratelimit.RateLimitRule;
import com.shareride.gateway.ratelimit.RateLimitTier;
import com.shareride.gateway.service.impl.RateLimiterServiceImpl;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@DisplayName("RateLimiterService Unit Tests")
class RateLimiterServiceTest {

    private RateLimiterConfig config;
    private RateLimiterServiceImpl rateLimiterService;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
        rateLimiterService = new RateLimiterServiceImpl(config);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> mockProvider(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    @Test
    @DisplayName("Should resolve ANONYMOUS tier for unauthenticated request")
    void shouldResolveAnonymousTierForUnauthenticated() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitTier tier = rateLimiterService.resolveTier(exchange);
        assertThat(tier).isEqualTo(RateLimitTier.ANONYMOUS);
    }

    @Test
    @DisplayName("Should resolve FREE tier for standard authenticated user")
    void shouldResolveFreeTierForStandardUser() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.USER_ID_HEADER, "user-123")
                        .header(GatewayConstants.USER_ROLES_HEADER, "ROLE_RIDER")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitTier tier = rateLimiterService.resolveTier(exchange);
        assertThat(tier).isEqualTo(RateLimitTier.FREE);
    }

    @Test
    @DisplayName("Should resolve PREMIUM tier for user with ROLE_PREMIUM")
    void shouldResolvePremiumTierForPremiumRole() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.USER_ID_HEADER, "user-456")
                        .header(GatewayConstants.USER_ROLES_HEADER, "ROLE_RIDER,ROLE_PREMIUM")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitTier tier = rateLimiterService.resolveTier(exchange);
        assertThat(tier).isEqualTo(RateLimitTier.PREMIUM);
    }

    @Test
    @DisplayName("Should resolve PREMIUM tier for user with ROLE_ADMIN")
    void shouldResolvePremiumTierForAdminRole() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.USER_ID_HEADER, "admin-1")
                        .header(GatewayConstants.USER_ROLES_HEADER, "ROLE_ADMIN")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitTier tier = rateLimiterService.resolveTier(exchange);
        assertThat(tier).isEqualTo(RateLimitTier.PREMIUM);
    }

    @Test
    @DisplayName("Should resolve PREMIUM tier for user with X-User-Tier header")
    void shouldResolvePremiumTierFromTierHeader() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .header(GatewayConstants.USER_ID_HEADER, "vip-user")
                        .header(GatewayConstants.USER_TIER_HEADER, "premium")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitTier tier = rateLimiterService.resolveTier(exchange);
        assertThat(tier).isEqualTo(RateLimitTier.PREMIUM);
    }

    @Test
    @DisplayName("Should match endpoint-specific rule for sensitive auth endpoints (login)")
    void shouldMatchEndpointSpecificRuleForLogin() {
        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitRule rule = rateLimiterService.resolveRule(exchange);
        assertThat(rule.getReplenishRate()).isEqualTo(5);
        assertThat(rule.getBurstCapacity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should match endpoint-specific rule for public discovery (/.well-known/**)")
    void shouldMatchEndpointSpecificRuleForWellKnown() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/.well-known/jwks.json")
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitRule rule = rateLimiterService.resolveRule(exchange);
        assertThat(rule.getReplenishRate()).isEqualTo(50);
        assertThat(rule.getBurstCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should enforce burst limit and reject requests exceeding capacity")
    void shouldEnforceBurstLimitAndRejectExceedingRequests() {
        // Configure strict 2 burst limit for testing
        config.setEndpoints(
                List.of(
                        EndpointRateLimitRule.builder()
                                .pathPattern("/api/v1/test/burst")
                                .method("GET")
                                .replenishRate(1)
                                .burstCapacity(2)
                                .requestedTokens(1)
                                .build()));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test/burst")
                        .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                        .build();

        // 1st request: allowed, remaining 1
        MockServerWebExchange ex1 = MockServerWebExchange.from(request);
        RateLimitResult r1 = rateLimiterService.evaluateRateLimit(ex1).block();
        assertThat(r1).isNotNull();
        assertThat(r1.allowed()).isTrue();
        assertThat(r1.limit()).isEqualTo(2);
        assertThat(r1.remaining()).isEqualTo(1);
        assertThat(r1.resetSeconds()).isGreaterThan(0);

        // 2nd request: allowed, remaining 0
        MockServerWebExchange ex2 = MockServerWebExchange.from(request);
        RateLimitResult r2 = rateLimiterService.evaluateRateLimit(ex2).block();
        assertThat(r2).isNotNull();
        assertThat(r2.allowed()).isTrue();
        assertThat(r2.remaining()).isEqualTo(0);

        // 3rd request: rejected (exceeds burst capacity)
        MockServerWebExchange ex3 = MockServerWebExchange.from(request);
        RateLimitResult r3 = rateLimiterService.evaluateRateLimit(ex3).block();
        assertThat(r3).isNotNull();
        assertThat(r3.allowed()).isFalse();
        assertThat(r3.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should provide higher limits for premium tier compared to free tier")
    void shouldProvideHigherLimitsForPremiumComparedToFree() {
        MockServerHttpRequest freeReq =
                MockServerHttpRequest.get("/api/v1/rides/history")
                        .header(GatewayConstants.USER_ID_HEADER, "rider-1")
                        .header(GatewayConstants.USER_ROLES_HEADER, "ROLE_RIDER")
                        .build();
        RateLimitRule freeRule =
                rateLimiterService.resolveRule(MockServerWebExchange.from(freeReq));

        MockServerHttpRequest premReq =
                MockServerHttpRequest.get("/api/v1/rides/history")
                        .header(GatewayConstants.USER_ID_HEADER, "rider-2")
                        .header(GatewayConstants.USER_ROLES_HEADER, "ROLE_PREMIUM")
                        .build();
        RateLimitRule premRule =
                rateLimiterService.resolveRule(MockServerWebExchange.from(premReq));

        assertThat(premRule.getBurstCapacity()).isGreaterThan(freeRule.getBurstCapacity());
        assertThat(premRule.getReplenishRate()).isGreaterThan(freeRule.getReplenishRate());
        assertThat(freeRule.getReplenishRate()).isEqualTo(config.getFree().getReplenishRate());
        assertThat(premRule.getReplenishRate()).isEqualTo(config.getPremium().getReplenishRate());
    }

    @Test
    @DisplayName("Should evaluate rate limit with Redis when template is provided")
    @SuppressWarnings("unchecked")
    void shouldEvaluateRateLimitWithRedisTemplate() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        RedisScript<List<Long>> script = mock(RedisScript.class);

        // Mock redis returning: allowed=1, remaining=19, resetTimestamp=1762777200, retryAfter=0
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(List.class)))
                .thenReturn(Flux.just(List.of(1L, 19L, 1762777200L, 0L)));

        RateLimiterServiceImpl serviceWithRedis =
                new RateLimiterServiceImpl(
                        config, mockProvider(redisTemplate), mockProvider(script));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .remoteAddress(new InetSocketAddress("10.1.2.3", 8080))
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(serviceWithRedis.evaluateRateLimit(exchange))
                .assertNext(
                        result -> {
                            assertThat(result.allowed()).isTrue();
                            assertThat(result.remaining()).isEqualTo(19L);
                            assertThat(result.resetSeconds()).isEqualTo(1762777200L);
                            assertThat(result.retryAfterSeconds()).isEqualTo(0L);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail open when Redis throws an exception")
    @SuppressWarnings("unchecked")
    void shouldFailOpenWhenRedisThrowsException() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        RedisScript<List<Long>> script = mock(RedisScript.class);

        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(List.class)))
                .thenReturn(Flux.error(new RuntimeException("Redis connection refused")));

        config.setFailOpen(true);

        RateLimiterServiceImpl serviceWithRedis =
                new RateLimiterServiceImpl(
                        config, mockProvider(redisTemplate), mockProvider(script));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/test")
                        .remoteAddress(new InetSocketAddress("10.1.2.3", 8080))
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(serviceWithRedis.evaluateRateLimit(exchange))
                .assertNext(
                        result -> {
                            assertThat(result.allowed()).isTrue();
                            assertThat(result.remaining())
                                    .isEqualTo(config.getAnonymous().getBurstCapacity());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should bypass rate limiting when disabled")
    void shouldBypassWhenDisabled() {
        config.setEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RateLimitResult result = rateLimiterService.evaluateRateLimit(exchange).block();
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(-1);
    }
}
