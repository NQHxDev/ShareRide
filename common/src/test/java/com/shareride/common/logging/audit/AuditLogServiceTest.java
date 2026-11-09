package com.shareride.common.logging.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.shareride.common.filter.TraceIdContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditLogServiceTest {

    private DefaultAuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new DefaultAuditLogService();
        TraceIdContext.setTraceId("audit-trace-12345");
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
    }

    @Test
    void testLogAuthentication() {
        assertDoesNotThrow(
                () ->
                        auditLogService.logAuthentication(
                                "u-1001", "LOGIN_SUCCESS", "192.168.1.1", "device-ios-123"));

        assertDoesNotThrow(
                () ->
                        auditLogService.logAuthentication(
                                "u-1002",
                                "LOGIN_FAILURE",
                                "10.0.0.1",
                                "device-android",
                                Map.of("reason", "bad_password", "password", "secret")));
    }

    @Test
    void testLogAuthorization() {
        assertDoesNotThrow(
                () ->
                        auditLogService.logAuthorization(
                                "u-1001", "ACCESS_DENIED", "/admin/metrics", "GET"));

        assertDoesNotThrow(
                () ->
                        auditLogService.logAuthorization(
                                "u-1001",
                                "PERMITTED",
                                "/api/v1/profile",
                                "GET",
                                Map.of("role", "ROLE_USER")));
    }

    @Test
    void testLogDataAccess() {
        assertDoesNotThrow(
                () ->
                        auditLogService.logDataAccess(
                                "u-admin",
                                "VIEW_PROFILE",
                                "u-target-99",
                                Map.of("reason", "support_ticket_88", "token", "super_secret")));
    }

    @Test
    void testLogSecurityEvent() {
        SecurityAuditEvent event =
                SecurityAuditEvent.builder()
                        .eventType("BRUTE_FORCE_DETECTED")
                        .userId("attacker-ip")
                        .clientIp("203.0.113.1")
                        .action("POST")
                        .resource("/api/v1/auth/login")
                        .status("BLOCKED")
                        .details(Map.of("attempts", 25))
                        .build();

        assertDoesNotThrow(() -> auditLogService.logSecurityEvent(event));
        assertDoesNotThrow(() -> auditLogService.logSecurityEvent(null));
    }
}
