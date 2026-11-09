package com.shareride.common.logging.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard data transfer object representing a security audit event (logins, privilege escalations,
 * data access, auth failures).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityAuditEvent implements Serializable {

    @Builder.Default private String eventId = UUID.randomUUID().toString();

    private String eventType;
    private String userId;
    private String clientIp;
    private String deviceId;
    private String resource;
    private String action;
    private String status;

    @Builder.Default private Instant timestamp = Instant.now();

    private String traceId;
    private String correlationId;
    private Map<String, Object> details;
}
