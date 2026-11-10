package com.shareride.gateway.filter;

import com.shareride.gateway.config.SecurityHeadersConfig;
import com.shareride.gateway.constant.GatewayConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enterprise reactive GlobalFilter injecting industry-standard HTTP Security Headers.
 *
 * <p>Protects API clients against clickjacking, MIME-sniffing, XSS vulnerabilities, cross-domain
 * information leakage, and ensures TLS transport hardening across all routed and direct responses.
 *
 * <p>Utilizes Spring WebFlux {@link ServerHttpResponse#beforeCommit(java.util.function.Supplier)}
 * to ensure security headers are persistently attached prior to HTTP response streaming.
 */
@Slf4j
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    private final SecurityHeadersConfig config;

    public SecurityHeadersFilter(SecurityHeadersConfig config) {
        this.config = config != null ? config : new SecurityHeadersConfig();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();

        // Apply headers proactively to current response headers
        applySecurityHeaders(response.getHeaders());

        // Register beforeCommit hook to guarantee security headers are present before payload
        // commitment
        response.beforeCommit(
                () -> {
                    applySecurityHeaders(response.getHeaders());
                    return Mono.empty();
                });

        return chain.filter(exchange);
    }

    private void applySecurityHeaders(HttpHeaders headers) {
        setHeader(
                headers,
                GatewayConstants.HEADER_X_CONTENT_TYPE_OPTIONS,
                config.getContentTypeOptions());
        setHeader(headers, GatewayConstants.HEADER_X_FRAME_OPTIONS, config.getFrameOptions());
        setHeader(headers, GatewayConstants.HEADER_X_XSS_PROTECTION, config.getXssProtection());
        setHeader(
                headers,
                GatewayConstants.HEADER_STRICT_TRANSPORT_SECURITY,
                config.getStrictTransportSecurity());
        setHeader(
                headers,
                GatewayConstants.HEADER_CONTENT_SECURITY_POLICY,
                config.getContentSecurityPolicy());
        setHeader(headers, GatewayConstants.HEADER_REFERRER_POLICY, config.getReferrerPolicy());
        setHeader(
                headers, GatewayConstants.HEADER_PERMISSIONS_POLICY, config.getPermissionsPolicy());
        setHeader(
                headers,
                GatewayConstants.HEADER_X_PERMITTED_CROSS_DOMAIN_POLICIES,
                config.getPermittedCrossDomainPolicies());

        if (config.getCacheControl() != null && !config.getCacheControl().isBlank()) {
            setHeader(headers, HttpHeaders.CACHE_CONTROL, config.getCacheControl());
        }
        if (config.getPragma() != null && !config.getPragma().isBlank()) {
            setHeader(headers, HttpHeaders.PRAGMA, config.getPragma());
        }
        if (config.getExpires() != null && !config.getExpires().isBlank()) {
            setHeader(headers, HttpHeaders.EXPIRES, config.getExpires());
        }
    }

    private void setHeader(HttpHeaders headers, String headerName, String value) {
        if (value != null && !value.isBlank()) {
            if (config.isOverrideExisting() || headers.getFirst(headerName) == null) {
                headers.set(headerName, value);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }
}
