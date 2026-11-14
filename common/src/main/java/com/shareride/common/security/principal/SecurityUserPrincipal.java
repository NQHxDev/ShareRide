package com.shareride.common.security.principal;

import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * Represents the authenticated user principal within Spring Security's {@code
 * SecurityContextHolder}.
 *
 * <p>This immutable value object is constructed from {@code UserContext} by {@code
 * SecurityContextBridgeFilter} and encapsulates the caller's identity, roles, and permissions for
 * use in {@code @PreAuthorize} expressions and security evaluations.
 *
 * <p>All fields are non-null except {@code email}, {@code tenantId}, and {@code sessionId}, which
 * may be empty for service accounts or anonymous callers.
 */
@Value
@Builder
public class SecurityUserPrincipal {

    /** Unique account identifier (UUIDv7 format). */
    String userId;

    /** User's verified email address. May be null for service accounts. */
    String email;

    /** Multi-tenant identifier. May be null for default tenant. */
    String tenantId;

    /** Current active session UUID. May be null for stateless API calls. */
    String sessionId;

    /** Assigned RBAC roles (normalized uppercase, e.g., ADMIN, RIDER, DRIVER). */
    Set<String> roles;

    /** Assigned fine-grained permissions (lowercase code, e.g., account:read). */
    Set<String> permissions;
}
