package com.shareride.common.resilience.config;

import com.shareride.common.resilience.circuitbreaker.CircuitBreakerConfig;
import com.shareride.common.resilience.circuitbreaker.CircuitBreakerRegistry;
import com.shareride.common.resilience.executor.ResilientCallExecutor;
import com.shareride.common.resilience.ratelimit.DistributedRateLimiter;
import com.shareride.common.resilience.retry.RetryExecutor;
import com.shareride.common.resilience.retry.RetryPolicy;
import com.shareride.common.resilience.timeout.TimeoutExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Spring Boot auto-configuration providing production-ready resilience beans. */
@AutoConfiguration
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties properties) {
        var cbProps = properties.getCircuitBreaker();
        CircuitBreakerConfig config =
                CircuitBreakerConfig.builder()
                        .failureRateThreshold(cbProps.getFailureRateThreshold())
                        .slowCallRateThreshold(cbProps.getSlowCallRateThreshold())
                        .slowCallDurationThresholdMs(cbProps.getSlowCallDurationThresholdMs())
                        .slidingWindowSize(cbProps.getSlidingWindowSize())
                        .minimumNumberOfCalls(cbProps.getMinimumNumberOfCalls())
                        .waitDurationInOpenStateMs(cbProps.getWaitDurationInOpenStateMs())
                        .permittedNumberOfCallsInHalfOpenState(
                                cbProps.getPermittedNumberOfCallsInHalfOpenState())
                        .build();
        return new CircuitBreakerRegistry(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryExecutor retryExecutor(ResilienceProperties properties) {
        var retryProps = properties.getRetry();
        RetryPolicy policy =
                RetryPolicy.builder()
                        .maxAttempts(retryProps.getMaxAttempts())
                        .initialIntervalMs(retryProps.getInitialIntervalMs())
                        .multiplier(retryProps.getMultiplier())
                        .maxIntervalMs(retryProps.getMaxIntervalMs())
                        .jitterFactor(retryProps.getJitterFactor())
                        .build();
        return new RetryExecutor(policy);
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeoutExecutor timeoutExecutor(ResilienceProperties properties) {
        return new TimeoutExecutor(properties.getTimeout().getDefaultTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedRateLimiter distributedRateLimiter(
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        return new DistributedRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilientCallExecutor resilientCallExecutor(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryExecutor retryExecutor,
            TimeoutExecutor timeoutExecutor,
            ResilienceProperties properties) {
        return new ResilientCallExecutor(
                circuitBreakerRegistry,
                retryExecutor,
                timeoutExecutor,
                properties.getBulkhead().getDefaultMaxConcurrentCalls(),
                properties.getBulkhead().getDefaultMaxWaitMs(),
                properties.getTimeout().getDefaultTimeoutMs());
    }
}
