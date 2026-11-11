package com.shareride.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.security.annotation.LogicalOperator;
import com.shareride.common.security.annotation.RequirePermission;
import com.shareride.common.security.annotation.RequireRole;
import com.shareride.common.security.aspect.SecurityAuthorizationAspect;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityAuthorizationAspectTest {

    private SecurityAuthorizationAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new SecurityAuthorizationAspect();
        UserContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @RequireRole({"ROLE_ADMIN", "ADMIN"})
    static class AdminOnlyTarget {}

    @RequireRole(
            value = {"ROLE_ADMIN", "ROLE_SUPER_ADMIN"},
            logical = LogicalOperator.AND)
    static class SuperAdminTarget {}

    @RequirePermission({"ride:write"})
    static class RideWriteTarget {}

    @RequirePermission(
            value = {"ride:read", "ride:write"},
            logical = LogicalOperator.AND)
    static class RideReadWriteTarget {}

    @Test
    @DisplayName("Aspect: Unauthenticated caller throws UnauthorizedException")
    void testUnauthenticatedCaller() {
        RequireRole requireRole = AdminOnlyTarget.class.getAnnotation(RequireRole.class);
        assertThrows(UnauthorizedException.class, () -> aspect.authorizeRole(requireRole));

        RequirePermission requirePermission =
                RideWriteTarget.class.getAnnotation(RequirePermission.class);
        assertThrows(
                UnauthorizedException.class, () -> aspect.authorizePermission(requirePermission));
    }

    @Test
    @DisplayName("Aspect: RequireRole OR evaluation")
    void testRequireRoleOrEvaluation() {
        UserContext user = UserContext.builder().userId("u-1").roles(Set.of("ROLE_ADMIN")).build();
        UserContextHolder.setContext(user);

        RequireRole requireRole = AdminOnlyTarget.class.getAnnotation(RequireRole.class);
        assertDoesNotThrow(() -> aspect.authorizeRole(requireRole));

        // Rider user fails
        UserContext rider = UserContext.builder().userId("u-2").roles(Set.of("ROLE_RIDER")).build();
        UserContextHolder.setContext(rider);
        assertThrows(ForbiddenException.class, () -> aspect.authorizeRole(requireRole));
    }

    @Test
    @DisplayName("Aspect: RequireRole AND evaluation")
    void testRequireRoleAndEvaluation() {
        RequireRole requireRole = SuperAdminTarget.class.getAnnotation(RequireRole.class);

        // Only ROLE_ADMIN -> fails AND
        UserContext adminOnly =
                UserContext.builder().userId("u-1").roles(Set.of("ROLE_ADMIN")).build();
        UserContextHolder.setContext(adminOnly);
        assertThrows(ForbiddenException.class, () -> aspect.authorizeRole(requireRole));

        // Both roles -> passes
        UserContext superAdmin =
                UserContext.builder()
                        .userId("u-2")
                        .roles(Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN"))
                        .build();
        UserContextHolder.setContext(superAdmin);
        assertDoesNotThrow(() -> aspect.authorizeRole(requireRole));
    }

    @Test
    @DisplayName("Aspect: RequirePermission evaluation")
    void testRequirePermissionEvaluation() {
        RequirePermission writePerm = RideWriteTarget.class.getAnnotation(RequirePermission.class);
        RequirePermission readWritePerm =
                RideReadWriteTarget.class.getAnnotation(RequirePermission.class);

        UserContext user =
                UserContext.builder().userId("u-1").permissions(Set.of("ride:write")).build();
        UserContextHolder.setContext(user);

        assertDoesNotThrow(() -> aspect.authorizePermission(writePerm));
        assertThrows(ForbiddenException.class, () -> aspect.authorizePermission(readWritePerm));

        UserContext fullUser =
                UserContext.builder()
                        .userId("u-2")
                        .permissions(Set.of("ride:read", "ride:write"))
                        .build();
        UserContextHolder.setContext(fullUser);
        assertDoesNotThrow(() -> aspect.authorizePermission(readWritePerm));
    }
}
