package com.shareride.common.context;

import com.shareride.common.exception.UnauthorizedException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/** Thread-local holder for {@link UserContext} during request processing. */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {}

    /** Checks if a UserContext has been explicitly bound to the current thread. */
    public static boolean hasContext() {
        return HOLDER.get() != null;
    }

    /** Retrieves the raw context bound to the thread, or null if uninitialized/cleared. */
    public static UserContext getRawContext() {
        return HOLDER.get();
    }

    /** Retrieves the current UserContext or initializes an empty one if absent. */
    public static UserContext getContext() {
        UserContext ctx = HOLDER.get();
        if (ctx == null) {
            ctx = UserContext.anonymous();
            HOLDER.set(ctx);
        }
        return ctx;
    }

    /** Sets the current UserContext. */
    public static void setContext(UserContext context) {
        if (context == null) {
            clear();
        } else {
            HOLDER.set(context);
        }
    }

    /** Clears current thread context to avoid memory leak. */
    public static void clear() {
        HOLDER.remove();
    }

    /** Returns the current authenticated user ID if present. */
    public static Optional<String> getCurrentUserId() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.isAuthenticated())
                ? Optional.of(ctx.getUserId())
                : Optional.empty();
    }

    /** Returns the current user ID or throws UnauthorizedException if unauthenticated. */
    public static String getRequiredUserId() {
        return getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User is unauthenticated"));
    }

    /** Returns current tenant ID if present. */
    public static Optional<String> getTenantId() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.getTenantId() != null && !ctx.getTenantId().isBlank())
                ? Optional.of(ctx.getTenantId())
                : Optional.empty();
    }

    /** Returns correlation ID if present. */
    public static Optional<String> getCorrelationId() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.getCorrelationId() != null && !ctx.getCorrelationId().isBlank())
                ? Optional.of(ctx.getCorrelationId())
                : Optional.empty();
    }

    /** Returns request priority if present. */
    public static Optional<String> getPriority() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.getPriority() != null && !ctx.getPriority().isBlank())
                ? Optional.of(ctx.getPriority())
                : Optional.empty();
    }

    /** Returns active feature flags. */
    public static Set<String> getFeatureFlags() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.getFeatureFlags() != null)
                ? ctx.getFeatureFlags()
                : Collections.emptySet();
    }

    /** Returns the current user email if present. */
    public static Optional<String> getCurrentEmail() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.getEmail() != null && !ctx.getEmail().isBlank())
                ? Optional.of(ctx.getEmail())
                : Optional.empty();
    }

    /** Returns user roles or empty set. */
    public static Set<String> getCurrentRoles() {
        UserContext ctx = HOLDER.get();
        return (ctx != null && ctx.getRoles() != null) ? ctx.getRoles() : Collections.emptySet();
    }

    /** Checks if current user has the specified role. */
    public static boolean hasRole(String role) {
        UserContext ctx = HOLDER.get();
        return ctx != null && ctx.hasRole(role);
    }

    /** Checks if current user has any of the specified roles. */
    public static boolean hasAnyRole(String... roles) {
        UserContext ctx = HOLDER.get();
        return ctx != null && ctx.hasAnyRole(roles);
    }

    /** Checks whether current user is authenticated. */
    public static boolean isAuthenticated() {
        UserContext ctx = HOLDER.get();
        return ctx != null && ctx.isAuthenticated();
    }

    /** Returns the client IP if present. */
    public static String getClientIp() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.getClientIp() : null;
    }

    /** Returns the trace ID attached to the context. */
    public static String getTraceId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.getTraceId() : null;
    }

    /** Returns the span ID attached to the context. */
    public static String getSpanId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.getSpanId() : null;
    }

    /** Returns the device ID attached to the context. */
    public static String getDeviceId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.getDeviceId() : null;
    }
}
