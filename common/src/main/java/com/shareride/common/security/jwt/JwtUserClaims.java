package com.shareride.common.security.jwt;

import com.shareride.common.context.UserContext;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

/**
 * Immutable value object representing verified, tamper-proof claims extracted from an enterprise
 * JWT. Provides type-safe accessors and helper methods for identity, roles, permissions, and
 * context propagation.
 */
@Builder
public record JwtUserClaims(
        String jti,
        UUID accountId,
        String email,
        Set<String> roles,
        Set<String> permissions,
        UUID sessionId,
        String deviceId,
        String tenantId,
        String issuer,
        List<String> audiences,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt,
        String version)
        implements Serializable {

    public JwtUserClaims {
        roles = roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
        permissions =
                permissions != null
                        ? Collections.unmodifiableSet(permissions)
                        : Collections.emptySet();
        audiences =
                audiences != null
                        ? Collections.unmodifiableList(audiences)
                        : Collections.emptyList();
    }

    /**
     * Checks if current principal has the specified role (case-insensitive, supports optional
     * "ROLE_" prefix matching).
     *
     * @param role Role code to check
     * @return true if role exists in token claims
     */
    public boolean hasRole(String role) {
        if (role == null || role.isBlank() || roles.isEmpty()) {
            return false;
        }
        String normalized = role.trim();
        String withPrefix = normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
        String withoutPrefix =
                normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;

        for (String r : roles) {
            if (r.equalsIgnoreCase(normalized)
                    || r.equalsIgnoreCase(withPrefix)
                    || r.equalsIgnoreCase(withoutPrefix)) {
                return true;
            }
        }
        return false;
    }

    /** Checks if current principal has any of the specified roles. */
    public boolean hasAnyRole(String... checkRoles) {
        if (checkRoles == null || checkRoles.length == 0) {
            return false;
        }
        for (String r : checkRoles) {
            if (hasRole(r)) {
                return true;
            }
        }
        return false;
    }

    /** Checks if current principal has the specified fine-grained permission code. */
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank() || permissions.isEmpty()) {
            return false;
        }
        String normalized = permission.trim().toLowerCase();
        return permissions.stream().anyMatch(p -> p.equalsIgnoreCase(normalized));
    }

    /** Checks if this token is currently expired against standard system clock. */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Converts verified JWT claims into a standard {@link UserContext} suitable for thread-binding
     * and inter-service HTTP header forwarding.
     */
    public UserContext toUserContext() {
        return UserContext.builder()
                .userId(accountId != null ? accountId.toString() : null)
                .email(email)
                .tenantId(tenantId)
                .sessionId(sessionId != null ? sessionId.toString() : null)
                .deviceId(deviceId)
                .roles(roles)
                .build();
    }
}
