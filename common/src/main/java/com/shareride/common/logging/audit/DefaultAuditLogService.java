package com.shareride.common.logging.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.masking.DataMaskingUtils;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** Default implementation of {@link AuditLogService} using a dedicated security audit logger. */
public class DefaultAuditLogService implements AuditLogService {

    private static final Logger auditLogger =
            LoggerFactory.getLogger("com.shareride.security.audit");
    private static final Marker AUDIT_MARKER = MarkerFactory.getMarker("SECURITY_AUDIT");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void logAuthentication(
            String userId, String eventType, String clientIp, String deviceId) {
        logAuthentication(userId, eventType, clientIp, deviceId, Map.of());
    }

    @Override
    public void logAuthentication(
            String userId,
            String eventType,
            String clientIp,
            String deviceId,
            Map<String, Object> details) {
        SecurityAuditEvent event =
                SecurityAuditEvent.builder()
                        .eventType(eventType)
                        .userId(userId)
                        .clientIp(clientIp)
                        .deviceId(deviceId)
                        .status(eventType.endsWith("SUCCESS") ? "SUCCESS" : "FAILURE")
                        .traceId(TraceIdContext.getTraceId())
                        .details(DataMaskingUtils.maskParams(details))
                        .build();
        logSecurityEvent(event);
    }

    @Override
    public void logAuthorization(String userId, String status, String resource, String action) {
        logAuthorization(userId, status, resource, action, Map.of());
    }

    @Override
    public void logAuthorization(
            String userId,
            String status,
            String resource,
            String action,
            Map<String, Object> details) {
        SecurityAuditEvent event =
                SecurityAuditEvent.builder()
                        .eventType("AUTHORIZATION")
                        .userId(userId)
                        .status(status)
                        .resource(resource)
                        .action(action)
                        .traceId(TraceIdContext.getTraceId())
                        .details(DataMaskingUtils.maskParams(details))
                        .build();
        logSecurityEvent(event);
    }

    @Override
    public void logDataAccess(
            String userId, String action, String targetUserId, Map<String, Object> details) {
        SecurityAuditEvent event =
                SecurityAuditEvent.builder()
                        .eventType("DATA_ACCESS")
                        .userId(userId)
                        .action(action)
                        .resource("user:" + targetUserId)
                        .status("SUCCESS")
                        .traceId(TraceIdContext.getTraceId())
                        .details(DataMaskingUtils.maskParams(details))
                        .build();
        logSecurityEvent(event);
    }

    @Override
    public void logSecurityEvent(SecurityAuditEvent event) {
        if (event == null) {
            return;
        }
        if (event.getTraceId() == null) {
            event.setTraceId(TraceIdContext.getTraceId());
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            auditLogger.info(AUDIT_MARKER, "[SECURITY_AUDIT] {}", json);
        } catch (Exception e) {
            auditLogger.info(
                    AUDIT_MARKER,
                    "[SECURITY_AUDIT] type={} user={} ip={} status={} resource={} traceId={}",
                    event.getEventType(),
                    event.getUserId(),
                    event.getClientIp(),
                    event.getStatus(),
                    event.getResource(),
                    event.getTraceId());
        }
    }
}
