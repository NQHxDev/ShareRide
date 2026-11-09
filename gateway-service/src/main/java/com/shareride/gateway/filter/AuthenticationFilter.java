package com.shareride.gateway.filter;

import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global Authentication & Token Forwarding Filter.
 *
 * <p>Enforces API security at the Gateway boundary. Public endpoints (such as login, registration,
 * health probes, and fallbacks) bypass authentication. For protected endpoints, it prepares
 * integration hooks for JWT validation, security audit logging, and context enrichment.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final GatewayMetricsService gatewayMetricsService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Whitelisted public path patterns that do not require authentication. */
    private static final List<String> PUBLIC_ENDPOINTS =
            List.of("/api/v1/auth/login", "/api/v1/auth/register", "/actuator/**", "/fallback/**");

    @org.springframework.beans.factory.annotation.Autowired
    public AuthenticationFilter(
            GatewayMetricsService gatewayMetricsService,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.gatewayMetricsService = gatewayMetricsService;
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    public AuthenticationFilter(GatewayMetricsService gatewayMetricsService) {
        this(gatewayMetricsService, null);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. Bypass authentication for public whitelist endpoints
        if (isPublicEndpoint(path)) {
            gatewayMetricsService.recordAuthEvent("PUBLIC_BYPASS", path, "whitelisted");
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 2. Enforce Bearer token inspection on protected endpoints
        if (authHeader == null || !authHeader.startsWith(GatewayConstants.BEARER_PREFIX)) {
            gatewayMetricsService.recordAuthEvent("MISSING_TOKEN", path, "no_bearer_token");
            AuditLogService auditService =
                    (auditLogServiceProvider != null)
                            ? auditLogServiceProvider.getIfAvailable()
                            : null;
            if (auditService != null) {
                String clientIp = resolveClientIp(request);
                auditService.logAuthentication(
                        "anonymous",
                        "AUTH_FAILURE",
                        clientIp,
                        "gateway",
                        Map.of("path", path, "reason", "missing_or_malformed_bearer_token"));
            }
            log.debug("Protected route accessed without Bearer token: [{}]", path);
        } else {
            gatewayMetricsService.recordAuthEvent("TOKEN_PRESENT", path, "bearer");
        }

        return chain.filter(exchange);
    }

    private boolean isPublicEndpoint(String path) {
        for (String pattern : PUBLIC_ENDPOINTS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(GatewayConstants.FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst(GatewayConstants.REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return GatewayConstants.UNKNOWN_IP;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
