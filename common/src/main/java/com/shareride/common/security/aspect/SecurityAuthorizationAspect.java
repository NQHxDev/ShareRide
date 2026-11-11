package com.shareride.common.security.aspect;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.security.annotation.LogicalOperator;
import com.shareride.common.security.annotation.RequirePermission;
import com.shareride.common.security.annotation.RequireRole;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Aspect enforcing declarative role and fine-grained permission authorization guards.
 *
 * <p>Intercepts methods or classes annotated with {@link RequireRole} or {@link RequirePermission}.
 * Ensures caller is authenticated, then evaluates role/permission constraints against {@link
 * UserContextHolder}. Throws {@link UnauthorizedException} (401) or {@link ForbiddenException}
 * (403).
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class SecurityAuthorizationAspect {

    @Before("@annotation(requireRole) || @within(requireRole)")
    public void authorizeRole(RequireRole requireRole) {
        if (requireRole == null || requireRole.value() == null || requireRole.value().length == 0) {
            return;
        }

        UserContext userContext = UserContextHolder.requireAuthenticated();
        String[] roles = requireRole.value();
        boolean authorized;

        if (requireRole.logical() == LogicalOperator.AND) {
            authorized = userContext.hasAllRoles(roles);
        } else {
            authorized = userContext.hasAnyRole(roles);
        }

        if (!authorized) {
            log.warn(
                    "Access denied: User [{}] lacks required roles {} (logical={})",
                    userContext.getUserId(),
                    Arrays.toString(roles),
                    requireRole.logical());
            throw new ForbiddenException(
                    "Access denied: Missing required role(s) " + Arrays.toString(roles));
        }
    }

    @Before("@annotation(requirePermission) || @within(requirePermission)")
    public void authorizePermission(RequirePermission requirePermission) {
        if (requirePermission == null
                || requirePermission.value() == null
                || requirePermission.value().length == 0) {
            return;
        }

        UserContext userContext = UserContextHolder.requireAuthenticated();
        String[] permissions = requirePermission.value();
        boolean authorized;

        if (requirePermission.logical() == LogicalOperator.AND) {
            authorized = userContext.hasAllPermissions(permissions);
        } else {
            authorized = userContext.hasAnyPermission(permissions);
        }

        if (!authorized) {
            log.warn(
                    "Access denied: User [{}] lacks required permissions {} (logical={})",
                    userContext.getUserId(),
                    Arrays.toString(permissions),
                    requirePermission.logical());
            throw new ForbiddenException(
                    "Access denied: Missing required permission(s) "
                            + Arrays.toString(permissions));
        }
    }
}
