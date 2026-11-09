package com.shareride.gateway.constant;

/** Technical and architecture constants for ShareRide API Gateway. */
public final class GatewayConstants {

    private GatewayConstants() {
        // Prevent instantiation of utility class
    }

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String REAL_IP_HEADER = "X-Real-IP";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public static final String BEARER_PREFIX = "Bearer ";
    public static final int BEARER_PREFIX_LENGTH = 7;

    public static final String AUTH_CIRCUIT_BREAKER = "authServiceCircuitBreaker";
    public static final String DEFAULT_CIRCUIT_BREAKER = "defaultCircuitBreaker";

    public static final String FALLBACK_AUTH_PATH = "/fallback/auth";
    public static final String FALLBACK_DEFAULT_PATH = "/fallback/default";

    public static final String ANONYMOUS_USER = "anonymous";
    public static final String UNKNOWN_IP = "unknown-ip";

    // Custom Metrics Names (automatically prefixed with 'shareride.' by facade)
    public static final String METRIC_GATEWAY_REQUESTS_TOTAL = "gateway.requests.total";
    public static final String METRIC_GATEWAY_REQUESTS_DURATION = "gateway.requests.duration";
    public static final String METRIC_GATEWAY_REQUESTS_ACTIVE = "gateway.requests.active";
    public static final String METRIC_GATEWAY_REQUESTS_SLOW = "gateway.requests.slow";
    public static final String METRIC_GATEWAY_RATELIMIT_REJECTED = "gateway.ratelimit.rejected";
    public static final String METRIC_GATEWAY_RATELIMIT_ALLOWED = "gateway.ratelimit.allowed";
    public static final String METRIC_GATEWAY_CIRCUITBREAKER_FALLBACK =
            "gateway.circuitbreaker.fallback";
    public static final String METRIC_GATEWAY_AUTH_EVENTS = "gateway.auth.events";
    public static final String METRIC_GATEWAY_ERRORS_TOTAL = "gateway.errors.total";
    public static final String METRIC_GATEWAY_REQUEST_SIZE = "gateway.request.size.bytes";
    public static final String METRIC_GATEWAY_RESPONSE_SIZE = "gateway.response.size.bytes";

    // Custom Metrics Tag Keys
    public static final String TAG_ROUTE_ID = "route_id";
    public static final String TAG_METHOD = "method";
    public static final String TAG_STATUS = "status";
    public static final String TAG_STATUS_GROUP = "status_group";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_LIMIT_TYPE = "limit_type";
    public static final String TAG_CLIENT_IP_PREFIX = "client_ip_prefix";
    public static final String TAG_CIRCUIT_BREAKER = "circuit_breaker";
    public static final String TAG_REASON = "reason";
    public static final String TAG_EVENT_TYPE = "event_type";
    public static final String TAG_PATH = "path";
    public static final String TAG_ERROR_CODE = "error_code";

    // Enterprise Security Headers
    public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    public static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    public static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";
    public static final String HEADER_X_PERMITTED_CROSS_DOMAIN_POLICIES =
            "X-Permitted-Cross-Domain-Policies";

    // Enterprise Security Headers Default Values
    public static final String DEFAULT_X_CONTENT_TYPE_OPTIONS = "nosniff";
    public static final String DEFAULT_X_FRAME_OPTIONS = "DENY";
    public static final String DEFAULT_X_XSS_PROTECTION = "0";
    public static final String DEFAULT_STRICT_TRANSPORT_SECURITY =
            "max-age=31536000; includeSubDomains; preload";
    public static final String DEFAULT_CONTENT_SECURITY_POLICY =
            "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";
    public static final String DEFAULT_REFERRER_POLICY = "strict-origin-when-cross-origin";
    public static final String DEFAULT_PERMISSIONS_POLICY =
            "geolocation=(), camera=(), microphone=(), payment=(), usb=()";
    public static final String DEFAULT_X_PERMITTED_CROSS_DOMAIN_POLICIES = "none";
}
