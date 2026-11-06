package com.shareride.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * Request execution context carrying user identity, tenant isolation,
 * business baggage, session, roles, and distributed tracing metadata.
 * Transferred across microservice boundaries via HTTP headers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext implements Serializable {

   private String userId;
   private String email;
   private String tenantId;
   private String correlationId;
   private String requestId;
   private String sessionId;
   private String priority;

   @Builder.Default
   private Set<String> roles = Collections.emptySet();

   @Builder.Default
   private Set<String> featureFlags = Collections.emptySet();

   private String clientIp;
   private String userAgent;
   private String deviceId;
   private String traceId;
   private String spanId;
   private String serviceName;
   private String apiVersion;

   /**
    * Checks if current context represents an authenticated user.
    */
   public boolean isAuthenticated() {
      return userId != null && !userId.isBlank();
   }

   /**
    * Checks if current request is marked with HIGH priority (SLA-sensitive).
    */
   public boolean isHighPriority() {
      return "HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority);
   }

   /**
    * Checks if a specific feature flag is active for this request.
    */
   public boolean hasFeatureFlag(String flag) {
      if (featureFlags == null || featureFlags.isEmpty() || flag == null) {
         return false;
      }
      return featureFlags.contains(flag.trim().toLowerCase());
   }

   /**
    * Checks if current user has the specified role (case-insensitive, supports
    * "ROLE_" prefix matching).
    */
   public boolean hasRole(String role) {
      if (roles == null || roles.isEmpty() || role == null || role.isBlank()) {
         return false;
      }

      String normalized = role.trim();
      String withPrefix = normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
      String withoutPrefix = normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;

      for (String r : roles) {
         if (r.equalsIgnoreCase(normalized) || r.equalsIgnoreCase(withPrefix) || r.equalsIgnoreCase(withoutPrefix)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Checks if current user has any of the specified roles.
    */
   public boolean hasAnyRole(String... checkRoles) {
      if (checkRoles == null || checkRoles.length == 0) {
         return false;
      }
      for (String r : checkRoles) {
         if (hasRole(r)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Checks if current user has all of the specified roles.
    */
   public boolean hasAllRoles(String... checkRoles) {
      if (checkRoles == null || checkRoles.length == 0) {
         return false;
      }
      for (String r : checkRoles) {
         if (!hasRole(r)) {
            return false;
         }
      }
      return true;
   }

   public static UserContext anonymous() {
      return UserContext.builder()
            .roles(Collections.emptySet())
            .featureFlags(Collections.emptySet())
            .build();
   }

}
