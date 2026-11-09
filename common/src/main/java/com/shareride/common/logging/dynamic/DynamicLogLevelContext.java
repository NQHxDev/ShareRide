package com.shareride.common.logging.dynamic;

import com.shareride.common.logging.MdcConstants;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;

/** Context and registry for dynamic runtime log level overrides per request or per user. */
public final class DynamicLogLevelContext {

    public static final String DEBUG_REQUEST_HEADER = "X-Debug-Request";
    public static final String DEBUG_USER_HEADER = "X-Debug-User";

    private static final ThreadLocal<Boolean> DEBUG_OVERRIDE_HOLDER = new ThreadLocal<>();
    private static final Set<String> DEBUG_USERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> DEBUG_ENDPOINTS = ConcurrentHashMap.newKeySet();

    private DynamicLogLevelContext() {}

    public static void setDebugOverride(boolean debug) {
        if (debug) {
            DEBUG_OVERRIDE_HOLDER.set(Boolean.TRUE);
            MDC.put(MdcConstants.DEBUG_MODE, "true");
            MDC.put(MdcConstants.LOG_LEVEL, "DEBUG");
        } else {
            clear();
        }
    }

    public static boolean isDebugActive() {
        Boolean b = DEBUG_OVERRIDE_HOLDER.get();
        if (b != null && b) {
            return true;
        }
        String mdcDebug = MDC.get(MdcConstants.DEBUG_MODE);
        return "true".equalsIgnoreCase(mdcDebug);
    }

    public static void addDebugUser(String userId) {
        if (userId != null && !userId.isBlank()) {
            DEBUG_USERS.add(userId.trim());
        }
    }

    public static void removeDebugUser(String userId) {
        if (userId != null) {
            DEBUG_USERS.remove(userId.trim());
        }
    }

    public static boolean isDebugUser(String userId) {
        return userId != null && DEBUG_USERS.contains(userId.trim());
    }

    public static void addDebugEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            DEBUG_ENDPOINTS.add(endpoint.trim());
        }
    }

    public static boolean isDebugEndpoint(String uri) {
        if (uri == null || DEBUG_ENDPOINTS.isEmpty()) {
            return false;
        }
        for (String pattern : DEBUG_ENDPOINTS) {
            if (uri.startsWith(pattern) || uri.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        DEBUG_OVERRIDE_HOLDER.remove();
        MDC.remove(MdcConstants.DEBUG_MODE);
        MDC.remove(MdcConstants.LOG_LEVEL);
    }
}
