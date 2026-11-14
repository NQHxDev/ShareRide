package com.shareride.common.security.constant;

/**
 * Security constants for RBAC roles, authorities, error codes, and localized messages.
 *
 * <p>This class consolidates all security-related string literals to enforce Constitution Principle
 * XI (No Hardcoding & DRY). All role names, authority prefixes, error codes, and user-facing error
 * messages are defined here and reused across the monorepo.
 */
public final class CommonSecurityConstants {

    // RBAC Role Names (uppercase)
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_RIDER = "RIDER";
    public static final String ROLE_DRIVER = "DRIVER";

    // Authority Prefixes
    public static final String AUTHORITY_ROLE_PREFIX = "ROLE_";
    public static final String AUTHORITY_PERM_PREFIX = "PERM_";

    // Security Error Codes
    public static final String ERROR_CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_CODE_FORBIDDEN = "FORBIDDEN";

    // Localized Error Messages (Vietnamese)
    public static final String ERROR_MESSAGE_UNAUTHORIZED =
            "Yêu cầu chưa được xác thực hoặc Access Token không hợp lệ";
    public static final String ERROR_MESSAGE_FORBIDDEN = "Không có quyền truy cập tài nguyên này";

    // Public Endpoint Patterns
    public static final String[] PUBLIC_ENDPOINTS = {
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/actuator/health/**",
        "/actuator/info"
    };

    private CommonSecurityConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
