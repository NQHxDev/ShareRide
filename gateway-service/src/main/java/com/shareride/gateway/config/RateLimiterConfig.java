package com.shareride.gateway.config;

import com.shareride.gateway.constant.GatewayConstants;
import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Enterprise Rate Limiting configuration for ShareRide API Gateway.
 *
 * <p>Provides distributed KeyResolvers for Redis/Valkey Token Bucket Rate Limiting by Client IP and
 * by Authenticated User ID.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimiterConfig {

    /**
     * Resolves Rate Limiting key based on the client IP address (from X-Forwarded-For, X-Real-IP,
     * or remote socket address).
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor =
                    exchange.getRequest()
                            .getHeaders()
                            .getFirst(GatewayConstants.FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return Mono.just(forwardedFor.split(",")[0].trim());
            }

            String realIp =
                    exchange.getRequest().getHeaders().getFirst(GatewayConstants.REAL_IP_HEADER);
            if (realIp != null && !realIp.isBlank()) {
                return Mono.just(realIp.trim());
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }

            return Mono.just(GatewayConstants.UNKNOWN_IP);
        };
    }

    /**
     * Resolves Rate Limiting key based on the authenticated User ID header (X-User-Id), falling
     * back to client IP if unauthenticated.
     */
    @Bean
    public KeyResolver userKeyResolver(KeyResolver ipKeyResolver) {
        return exchange -> {
            String userId =
                    exchange.getRequest().getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return ipKeyResolver.resolve(exchange).map(ip -> "ip:" + ip);
        };
    }
}
