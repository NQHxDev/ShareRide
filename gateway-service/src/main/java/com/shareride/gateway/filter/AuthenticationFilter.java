package com.shareride.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.ResponseLevel;
import com.shareride.common.security.jwt.JwtTokenVerifier;
import com.shareride.common.security.jwt.JwtUserClaims;
import com.shareride.gateway.constant.GatewayConstants;
import com.shareride.gateway.service.GatewayMetricsService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global Authentication & Token Forwarding Filter.
 *
 * <p>Enforces API security at the Gateway boundary. Public endpoints (such as login, registration,
 * JWKS discovery, health probes, and fallbacks) bypass authentication. For protected endpoints, it
 * verifies incoming asymmetric EdDSA (Ed25519) JWTs, enriches downstream request headers with
 * authenticated user identity and context, records security metrics and audit logs, and rejects
 * unauthorized or invalid requests with 401 Unauthorized.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final GatewayMetricsService gatewayMetricsService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final ObjectProvider<JwtTokenVerifier> jwtTokenVerifierProvider;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Whitelisted public path patterns that do not require authentication. */
    private static final List<String> PUBLIC_ENDPOINTS =
            List.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/jwks.json",
                    "/.well-known/**",
                    "/actuator/**",
                    "/fallback/**");

    @Autowired
    public AuthenticationFilter(
            GatewayMetricsService gatewayMetricsService,
            ObjectProvider<AuditLogService> auditLogServiceProvider,
            ObjectProvider<JwtTokenVerifier> jwtTokenVerifierProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.gatewayMetricsService = gatewayMetricsService;
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.jwtTokenVerifierProvider = jwtTokenVerifierProvider;
        this.objectMapper =
                (objectMapperProvider != null && objectMapperProvider.getIfAvailable() != null)
                        ? objectMapperProvider.getIfAvailable()
                        : new ObjectMapper();
    }

    public AuthenticationFilter(
            GatewayMetricsService gatewayMetricsService,
            ObjectProvider<AuditLogService> auditLogServiceProvider,
            ObjectProvider<JwtTokenVerifier> jwtTokenVerifierProvider) {
        this(gatewayMetricsService, auditLogServiceProvider, jwtTokenVerifierProvider, null);
    }

    public AuthenticationFilter(
            GatewayMetricsService gatewayMetricsService,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this(gatewayMetricsService, auditLogServiceProvider, null, null);
    }

    public AuthenticationFilter(GatewayMetricsService gatewayMetricsService) {
        this(gatewayMetricsService, null, null, null);
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

        JwtTokenVerifier verifier =
                (jwtTokenVerifierProvider != null)
                        ? jwtTokenVerifierProvider.getIfAvailable()
                        : null;

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 2. Validate Authorization header format
        if (authHeader == null || !authHeader.startsWith(GatewayConstants.BEARER_PREFIX)) {
            gatewayMetricsService.recordAuthEvent("MISSING_TOKEN", path, "no_bearer_token");
            recordAuditFailure(request, path, "anonymous", "missing_or_malformed_bearer_token");

            // If a token verifier is active, missing credentials must be rejected at perimeter
            if (verifier != null) {
                log.debug("Protected route accessed without Bearer token: [{}]", path);
                return writeUnauthorizedResponse(
                        exchange,
                        "Authentication credentials are required to access this resource");
            }

            return chain.filter(exchange);
        }

        // 3. Extract and verify JWT token
        String token = authHeader.substring(GatewayConstants.BEARER_PREFIX_LENGTH).trim();

        if (verifier == null) {
            // Backward-compatible passthrough if no verifier configured
            gatewayMetricsService.recordAuthEvent("TOKEN_PRESENT", path, "bearer");
            return chain.filter(exchange);
        }

        try {
            JwtUserClaims claims = verifier.verify(token);
            gatewayMetricsService.recordAuthEvent("TOKEN_VALIDATED", path, "success");

            // 4. Enrich downstream request with verified user context headers
            ServerHttpRequest.Builder requestBuilder = request.mutate();
            if (claims.accountId() != null) {
                requestBuilder.header(
                        GatewayConstants.USER_ID_HEADER, claims.accountId().toString());
            }
            if (claims.email() != null && !claims.email().isBlank()) {
                requestBuilder.header(GatewayConstants.USER_EMAIL_HEADER, claims.email());
            }
            if (claims.roles() != null && !claims.roles().isEmpty()) {
                requestBuilder.header(
                        GatewayConstants.USER_ROLES_HEADER, String.join(",", claims.roles()));
                if (claims.hasAnyRole(
                        "ROLE_PREMIUM", "PREMIUM", "ROLE_ADMIN", "ADMIN", "ROLE_VIP", "VIP")) {
                    requestBuilder.header(
                            GatewayConstants.USER_TIER_HEADER, GatewayConstants.TIER_PREMIUM);
                } else {
                    requestBuilder.header(
                            GatewayConstants.USER_TIER_HEADER, GatewayConstants.TIER_FREE);
                }
            }
            if (claims.permissions() != null && !claims.permissions().isEmpty()) {
                requestBuilder.header(
                        GatewayConstants.USER_PERMISSIONS_HEADER,
                        String.join(",", claims.permissions()));
            }
            if (claims.sessionId() != null) {
                requestBuilder.header(
                        GatewayConstants.SESSION_ID_HEADER, claims.sessionId().toString());
            }
            if (claims.tenantId() != null && !claims.tenantId().isBlank()) {
                requestBuilder.header(GatewayConstants.TENANT_ID_HEADER, claims.tenantId());
            }
            if (claims.deviceId() != null && !claims.deviceId().isBlank()) {
                requestBuilder.header(GatewayConstants.DEVICE_ID_HEADER, claims.deviceId());
            }

            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        } catch (Exception ex) {
            gatewayMetricsService.recordAuthEvent("TOKEN_INVALID", path, "verification_failed");
            recordAuditFailure(request, path, "unverified_token", ex.getMessage());
            log.warn(
                    "Perimeter token validation rejected for path [{}]: {}", path, ex.getMessage());
            return writeUnauthorizedResponse(
                    exchange, "The provided authentication token is invalid or expired");
        }
    }

    private boolean isPublicEndpoint(String path) {
        for (String pattern : PUBLIC_ENDPOINTS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void recordAuditFailure(
            ServerHttpRequest request, String path, String principal, String reason) {
        AuditLogService auditService =
                (auditLogServiceProvider != null) ? auditLogServiceProvider.getIfAvailable() : null;
        if (auditService != null) {
            String clientIp = resolveClientIp(request);
            auditService.logAuthentication(
                    principal,
                    "AUTH_FAILURE",
                    clientIp,
                    "gateway",
                    Map.of("path", path, "reason", reason));
        }
    }

    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId =
                exchange.getRequest().getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        if (correlationId != null) {
            response.getHeaders().set(GatewayConstants.CORRELATION_ID_HEADER, correlationId);
        }

        BaseResponse<Void> baseResponse =
                BaseResponse.<Void>builder()
                        .level(ResponseLevel.ERROR)
                        .fromResponseCode(ResponseCode.UNAUTHORIZED)
                        .message(message)
                        .correlationId(correlationId)
                        .path(exchange.getRequest().getPath().value())
                        .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(baseResponse);
        } catch (JsonProcessingException e) {
            bytes =
                    "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized\"}"
                            .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
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
