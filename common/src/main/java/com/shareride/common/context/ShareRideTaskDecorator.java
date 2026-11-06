package com.shareride.common.context;

import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.dynamic.DynamicLogLevelContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.HashSet;
import java.util.Map;

/**
 * Propagates {@link UserContext}, {@link TraceIdContext}, business baggage,
 * dynamic log level flags, and SLF4J {@link MDC} to asynchronous execution
 * threads.
 */
public class ShareRideTaskDecorator implements TaskDecorator {

   @Override
   public Runnable decorate(Runnable runnable) {
      // Snapshot context from caller thread
      Map<String, String> mdcMap = MDC.getCopyOfContextMap();
      UserContext userContext = UserContextHolder.getContext();
      String traceId = TraceIdContext.getTraceId();
      String spanId = TraceIdContext.getSpanId();
      String requestId = TraceIdContext.getRequestId();
      boolean debugActive = DynamicLogLevelContext.isDebugActive();

      UserContext copiedUserContext = (userContext != null) ? UserContext.builder()
            .userId(userContext.getUserId())
            .email(userContext.getEmail())
            .tenantId(userContext.getTenantId())
            .correlationId(userContext.getCorrelationId())
            .requestId(userContext.getRequestId())
            .sessionId(userContext.getSessionId())
            .priority(userContext.getPriority())
            .roles(userContext.getRoles() != null ? new HashSet<>(userContext.getRoles()) : null)
            .featureFlags(userContext.getFeatureFlags() != null ? new HashSet<>(userContext.getFeatureFlags()) : null)
            .clientIp(userContext.getClientIp())
            .userAgent(userContext.getUserAgent())
            .deviceId(userContext.getDeviceId())
            .traceId(userContext.getTraceId())
            .spanId(userContext.getSpanId())
            .serviceName(userContext.getServiceName())
            .build() : null;

      return () -> {
         Map<String, String> previousMdc = MDC.getCopyOfContextMap();
         UserContext previousUserContext = UserContextHolder.getContext();
         String previousTraceId = TraceIdContext.getTraceId();
         String previousSpanId = TraceIdContext.getSpanId();
         String previousRequestId = TraceIdContext.getRequestId();
         boolean previousDebug = DynamicLogLevelContext.isDebugActive();

         try {
            if (mdcMap != null) {
               MDC.setContextMap(mdcMap);
            } else {
               MDC.clear();
            }

            if (copiedUserContext != null) {
               UserContextHolder.setContext(copiedUserContext);
            } else {
               UserContextHolder.clear();
            }

            if (traceId != null) {
               TraceIdContext.setTraceId(traceId);
            }
            if (spanId != null) {
               TraceIdContext.setSpanId(spanId);
            }
            if (requestId != null) {
               TraceIdContext.setRequestId(requestId);
            }
            if (debugActive) {
               DynamicLogLevelContext.setDebugOverride(true);
            }

            runnable.run();
         } finally {
            if (previousMdc != null) {
               MDC.setContextMap(previousMdc);
            } else {
               MDC.clear();
            }

            if (previousUserContext != null) {
               UserContextHolder.setContext(previousUserContext);
            } else {
               UserContextHolder.clear();
            }

            if (previousTraceId != null) {
               TraceIdContext.setTraceId(previousTraceId);
            } else {
               TraceIdContext.clear();
            }

            if (previousSpanId != null) {
               TraceIdContext.setSpanId(previousSpanId);
            }
            if (previousRequestId != null) {
               TraceIdContext.setRequestId(previousRequestId);
            }
            if (!previousDebug) {
               DynamicLogLevelContext.clear();
            }
         }
      };
   }

}
