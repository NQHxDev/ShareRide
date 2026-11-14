package com.shareride.common.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.UnauthorizedException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
        CorrelationIdContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        CorrelationIdContext.clear();
    }

    @Test
    @DisplayName("UserContext role checks should support case-insensitivity and ROLE_ prefix")
    void testUserContextRoleChecks() {
        UserContext context =
                UserContext.builder()
                        .userId("usr-123")
                        .roles(Set.of("ROLE_DRIVER", "PASSENGER"))
                        .build();

        assertTrue(context.isAuthenticated());
        assertTrue(context.hasRole("DRIVER"), "Should match without ROLE_ prefix");
        assertTrue(context.hasRole("ROLE_DRIVER"), "Should match with ROLE_ prefix");
        assertTrue(context.hasRole("driver"), "Should match case-insensitively");
        assertTrue(context.hasRole("PASSENGER"));
        assertTrue(context.hasRole("ROLE_PASSENGER"));
        assertFalse(context.hasRole("ADMIN"));

        assertTrue(context.hasAnyRole("ADMIN", "DRIVER"));
        assertFalse(context.hasAnyRole("ADMIN", "SUPERUSER"));

        assertTrue(context.hasAllRoles("DRIVER", "PASSENGER"));
        assertFalse(context.hasAllRoles("DRIVER", "ADMIN"));
    }

    @Test
    @DisplayName("UserContextHolder should manage context lifecycle and accessors")
    void testUserContextHolderLifecycle() {
        // Default anonymous
        UserContext anon = UserContextHolder.getContext();
        assertNotNull(anon);
        assertFalse(anon.isAuthenticated());
        assertFalse(UserContextHolder.isAuthenticated());
        assertEquals(Optional.empty(), UserContextHolder.getCurrentUserId());

        assertThrows(UnauthorizedException.class, UserContextHolder::getRequiredUserId);

        // Set context
        UserContext user =
                UserContext.builder()
                        .userId("usr-456")
                        .email("driver@shareride.com")
                        .roles(Set.of("DRIVER"))
                        .clientIp("192.168.1.100")
                        .deviceId("device-xyz")
                        .traceId("trace-001")
                        .spanId("span-001")
                        .build();

        UserContextHolder.setContext(user);

        assertTrue(UserContextHolder.isAuthenticated());
        assertEquals(Optional.of("usr-456"), UserContextHolder.getCurrentUserId());
        assertEquals("usr-456", UserContextHolder.getRequiredUserId());
        assertEquals(Optional.of("driver@shareride.com"), UserContextHolder.getCurrentEmail());
        assertEquals(Set.of("DRIVER"), UserContextHolder.getCurrentRoles());
        assertTrue(UserContextHolder.hasRole("ROLE_DRIVER"));
        assertEquals("192.168.1.100", UserContextHolder.getClientIp());
        assertEquals("device-xyz", UserContextHolder.getDeviceId());
        assertEquals("trace-001", UserContextHolder.getTraceId());
        assertEquals("span-001", UserContextHolder.getSpanId());

        // Clear
        UserContextHolder.clear();
        assertFalse(UserContextHolder.isAuthenticated());
        assertEquals(Optional.empty(), UserContextHolder.getCurrentUserId());
    }

    @Test
    @DisplayName("UserContextHolder should provide thread isolation")
    void testThreadIsolation() throws InterruptedException {
        UserContext mainUser = UserContext.builder().userId("main-user").build();
        UserContextHolder.setContext(mainUser);

        AtomicBoolean threadPassed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread =
                new Thread(
                        () -> {
                            // New thread should not see main thread's context
                            Optional<String> otherUserId = UserContextHolder.getCurrentUserId();
                            if (otherUserId.isEmpty()) {
                                threadPassed.set(true);
                            }
                            latch.countDown();
                        });

        thread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(threadPassed.get());

        // Main thread context unaffected
        assertEquals("main-user", UserContextHolder.getRequiredUserId());
    }

    @Test
    @DisplayName("UserContext and UserContextHolder permission checks")
    void testPermissionChecks() {
        UserContext context =
                UserContext.builder()
                        .userId("usr-789")
                        .permissions(Set.of("account:read", "ride:accept"))
                        .build();

        UserContextHolder.setContext(context);

        assertTrue(UserContextHolder.hasPermission("account:read"));
        assertTrue(UserContextHolder.hasPermission("ACCOUNT:READ"), "Should be case-insensitive");
        assertFalse(UserContextHolder.hasPermission("account:delete"));

        assertTrue(UserContextHolder.hasAnyPermission("ride:accept", "admin:all"));
        assertFalse(UserContextHolder.hasAnyPermission("admin:all", "ride:cancel"));

        assertEquals(
                Set.of("account:read", "ride:accept"), UserContextHolder.getCurrentPermissions());

        UserContextHolder.requirePermission("account:read");
        UserContextHolder.requireAnyPermission("ride:accept", "dummy");
        assertThrows(
                ForbiddenException.class,
                () -> UserContextHolder.requirePermission("account:delete"));
    }

    @Test
    @DisplayName("UserContextHolder.requireSelfOrAdmin allows owner and admin, rejects others")
    void testRequireSelfOrAdmin() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        // 1. Unauthenticated -> UnauthorizedException
        assertThrows(
                UnauthorizedException.class, () -> UserContextHolder.requireSelfOrAdmin(ownerId));

        // 2. Authenticated as normal user -> matches own ID
        UserContext regularUser =
                UserContext.builder()
                        .userId(ownerId.toString())
                        .roles(Set.of("ROLE_RIDER"))
                        .build();
        UserContextHolder.setContext(regularUser);

        // Accessing own account -> success
        UserContextHolder.requireSelfOrAdmin(ownerId);
        UserContextHolder.requireSelfOrAdmin(ownerId.toString());

        // Accessing other account -> ForbiddenException
        assertThrows(ForbiddenException.class, () -> UserContextHolder.requireSelfOrAdmin(otherId));
        assertThrows(
                ForbiddenException.class,
                () -> UserContextHolder.requireSelfOrAdmin(otherId.toString()));

        // 3. Authenticated as ADMIN -> can access ANY account
        UserContext adminUser =
                UserContext.builder()
                        .userId(UUID.randomUUID().toString())
                        .roles(Set.of("ROLE_ADMIN"))
                        .build();
        UserContextHolder.setContext(adminUser);

        // Accessing any account -> success
        UserContextHolder.requireSelfOrAdmin(ownerId);
        UserContextHolder.requireSelfOrAdmin(otherId);

        // Null UUID target
        assertThrows(
                IllegalArgumentException.class,
                () -> UserContextHolder.requireSelfOrAdmin((UUID) null));
    }

    @Test
    @DisplayName("UserContextHolder: HasContext, RawContext and SetContext(null)")
    void testContextStateAndNullClearing() {
        assertFalse(UserContextHolder.hasContext());
        org.junit.jupiter.api.Assertions.assertNull(UserContextHolder.getRawContext());

        UserContext ctx = UserContext.builder().userId("u-123").build();
        UserContextHolder.setContext(ctx);
        assertTrue(UserContextHolder.hasContext());
        assertEquals(ctx, UserContextHolder.getRawContext());

        UserContextHolder.setContext(null);
        assertFalse(UserContextHolder.hasContext());
        org.junit.jupiter.api.Assertions.assertNull(UserContextHolder.getRawContext());
    }

    @Test
    @DisplayName("UserContextHolder: Accessors when context is null")
    void testAccessorsWhenContextIsNull() {
        UserContextHolder.clear();
        CorrelationIdContext.clear();
        assertFalse(UserContextHolder.hasRole("ADMIN"));
        assertFalse(UserContextHolder.hasAnyRole("ADMIN"));
        assertFalse(UserContextHolder.hasPermission("read"));
        assertFalse(UserContextHolder.hasAnyPermission("read"));
        assertTrue(UserContextHolder.getTenantId().isEmpty());
        assertTrue(UserContextHolder.getPriority().isEmpty());
        assertTrue(UserContextHolder.getFeatureFlags().isEmpty());
        assertTrue(UserContextHolder.getCurrentRoles().isEmpty());
        assertTrue(UserContextHolder.getCurrentPermissions().isEmpty());
        org.junit.jupiter.api.Assertions.assertNull(UserContextHolder.getClientIp());
        org.junit.jupiter.api.Assertions.assertNull(UserContextHolder.getTraceId());
        org.junit.jupiter.api.Assertions.assertNull(UserContextHolder.getSpanId());
        org.junit.jupiter.api.Assertions.assertNull(UserContextHolder.getDeviceId());
        assertTrue(UserContextHolder.getCorrelationId().isEmpty());
    }

    @Test
    @DisplayName("UserContextHolder: Correlation ID fallback to CorrelationIdContext")
    void testCorrelationIdResolution() {
        UserContextHolder.clear();
        CorrelationIdContext.clear();

        // 1. None present
        assertTrue(UserContextHolder.getCorrelationId().isEmpty());

        // 2. CorrelationIdContext has it
        CorrelationIdContext.setCorrelationId("cid-fallback-123");
        assertEquals(Optional.of("cid-fallback-123"), UserContextHolder.getCorrelationId());

        // 3. UserContext overrides CorrelationIdContext
        UserContext ctx = UserContext.builder().correlationId("cid-user-456").build();
        UserContextHolder.setContext(ctx);
        assertEquals(Optional.of("cid-user-456"), UserContextHolder.getCorrelationId());

        CorrelationIdContext.clear();
    }

    @Test
    @DisplayName("UserContextHolder: RequireRole and RequireAnyRole validation")
    void testRequireRoleAndRequireAnyRole() {
        UserContext user = UserContext.builder().userId("u-1").roles(Set.of("ROLE_DRIVER")).build();
        UserContextHolder.setContext(user);

        UserContextHolder.requireRole("DRIVER");
        UserContextHolder.requireRole("ROLE_DRIVER");
        assertThrows(ForbiddenException.class, () -> UserContextHolder.requireRole("ADMIN"));

        UserContextHolder.requireAnyRole("ADMIN", "DRIVER");
        assertThrows(
                ForbiddenException.class, () -> UserContextHolder.requireAnyRole("ADMIN", "SUPER"));
    }

    @Test
    @DisplayName("UserContext: Edge cases for flags, priority, and role/permission checks")
    void testUserContextEdgeCases() {
        UserContext ctx =
                UserContext.builder()
                        .userId("u-99")
                        .priority("HIGH")
                        .featureFlags(Set.of("surge_pricing", "instant_booking"))
                        .build();

        assertTrue(ctx.isHighPriority());
        ctx.setPriority("URGENT");
        assertTrue(ctx.isHighPriority());
        ctx.setPriority("NORMAL");
        assertFalse(ctx.isHighPriority());
        ctx.setPriority(null);
        assertFalse(ctx.isHighPriority());

        assertTrue(ctx.hasFeatureFlag("surge_pricing"));
        assertFalse(ctx.hasFeatureFlag("unknown_flag"));
        assertFalse(ctx.hasFeatureFlag(null));

        UserContext emptyFlags = UserContext.builder().featureFlags(null).build();
        assertFalse(emptyFlags.hasFeatureFlag("any"));

        // Roles edge cases
        UserContext noRoles = UserContext.builder().roles(null).build();
        assertFalse(noRoles.hasRole(null));
        assertFalse(noRoles.hasRole("  "));
        assertFalse(noRoles.hasRole("DRIVER"));
        assertFalse(noRoles.hasAnyRole((String[]) null));
        assertFalse(noRoles.hasAnyRole());
        assertFalse(noRoles.hasAllRoles((String[]) null));
        assertFalse(noRoles.hasAllRoles());

        // Permissions edge cases
        UserContext noPerms = UserContext.builder().permissions(null).build();
        assertFalse(noPerms.hasPermission(null));
        assertFalse(noPerms.hasPermission("  "));
        assertFalse(noPerms.hasPermission("read"));
        assertFalse(noPerms.hasAnyPermission((String[]) null));
        assertFalse(noPerms.hasAnyPermission());
        assertFalse(noPerms.hasAllPermissions((String[]) null));
        assertFalse(noPerms.hasAllPermissions());

        UserContext withPerms =
                UserContext.builder().permissions(Set.of("ride:read", "ride:write")).build();
        assertTrue(withPerms.hasAllPermissions("ride:read", "ride:write"));
        assertFalse(withPerms.hasAllPermissions("ride:read", "ride:delete"));
    }
}
