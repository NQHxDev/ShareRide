package com.shareride.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reactive Resilience4j Circuit Breaker and Time Limiter configuration for API Gateway. */
@Configuration(proxyBeanMethods = false)
public class ResilienceConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory ->
                factory.configureDefault(
                        id ->
                                new Resilience4JConfigBuilder(id)
                                        .circuitBreakerConfig(
                                                CircuitBreakerConfig.custom()
                                                        .slidingWindowSize(10)
                                                        .failureRateThreshold(50.0f)
                                                        .waitDurationInOpenState(
                                                                Duration.ofSeconds(5))
                                                        .permittedNumberOfCallsInHalfOpenState(3)
                                                        .slowCallRateThreshold(50.0f)
                                                        .slowCallDurationThreshold(
                                                                Duration.ofSeconds(2))
                                                        .build())
                                        .timeLimiterConfig(
                                                TimeLimiterConfig.custom()
                                                        .timeoutDuration(Duration.ofSeconds(3))
                                                        .build())
                                        .build());
    }
}
