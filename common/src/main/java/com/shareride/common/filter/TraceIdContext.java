package com.shareride.common.filter;

import java.util.UUID;

/**
 * Thread-local context for distributed tracing (traceId/requestId).
 */
public final class TraceIdContext {

   public static final String TRACE_HEADER = "X-Trace-Id";
   public static final String REQUEST_ID_HEADER = "X-Request-Id";

   private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

   private TraceIdContext() {
   }

   public static void setTraceId(String traceId) {
      TRACE_ID_HOLDER.set(traceId);
   }

   public static String getTraceId() {
      String current = TRACE_ID_HOLDER.get();
      if (current == null || current.isBlank()) {
         current = generateTraceId();
         TRACE_ID_HOLDER.set(current);
      }
      return current;
   }

   public static void clear() {
      TRACE_ID_HOLDER.remove();
   }

   public static String generateTraceId() {
      return UUID.randomUUID().toString().replace("-", "");
   }
}
