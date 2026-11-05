package com.shareride.common.logging.audit;

import java.util.Map;

/**
 * Enterprise service interface for recording security and compliance audit logs.
 */
public interface AuditLogService {

    void logAuthentication(String userId, String eventType, String clientIp, String deviceId);

    void logAuthentication(String userId, String eventType, String clientIp, String deviceId, Map<String, Object> details);

    default void logAuthorization(String userId, String status, String resource) {
        logAuthorization(userId, status, resource, null, Map.of());
    }

    void logAuthorization(String userId, String status, String resource, String action);

    void logAuthorization(String userId, String status, String resource, String action, Map<String, Object> details);

    default void logDataAccess(String userId, String action, String targetUserId) {
        logDataAccess(userId, action, targetUserId, Map.of());
    }

    void logDataAccess(String userId, String action, String targetUserId, Map<String, Object> details);

    void logSecurityEvent(SecurityAuditEvent event);
}
