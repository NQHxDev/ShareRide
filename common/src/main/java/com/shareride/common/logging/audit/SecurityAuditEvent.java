package com.shareride.common.logging.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard security audit record for tracking authentication, authorization,
 * sensitive data access, rate limiting, and suspicious events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityAuditEvent implements Serializable {

   @Builder.Default
   private String eventId = UUID.randomUUID().toString();

   private String eventType;
   private String userId;
   private String clientIp;
   private String deviceId;
   private String resource;
   private String action;
   private String status;

   @Builder.Default
   private Instant timestamp = Instant.now();

   private String traceId;
   private Map<String, Object> details;

}
