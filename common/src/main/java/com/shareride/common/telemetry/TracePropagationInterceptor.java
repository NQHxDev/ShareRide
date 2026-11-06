package com.shareride.common.telemetry;

import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.filter.UserContextFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client HTTP interceptor that propagates distributed tracing headers
 * (W3C traceparent, X-Trace-Id, X-Request-Id, X-Span-Id) and business context
 * baggage
 * (Tenant ID, User ID, Session ID, Correlation ID, Request Priority, Feature
 * Flags)
 * to downstream microservices in the service mesh.
 */
public class TracePropagationInterceptor implements ClientHttpRequestInterceptor {

   private final ObjectProvider<Tracer> tracerProvider;

   public TracePropagationInterceptor() {
      this.tracerProvider = null;
   }

   public TracePropagationInterceptor(ObjectProvider<Tracer> tracerProvider) {
      this.tracerProvider = tracerProvider;
   }

   @Override
   public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
         throws IOException {

      // 1. Trace ID, Span ID & Request ID propagation
      String traceId = null;
      String spanId = null;
      boolean sampled = true;

      if (tracerProvider != null) {
         Tracer tracer = tracerProvider.getIfAvailable();
         if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
               traceId = currentSpan.context().traceId();
               spanId = currentSpan.context().spanId();
               Boolean s = currentSpan.context().sampled();
               if (s != null) {
                  sampled = s;
               }
            }
         }
      }

      if (traceId == null || traceId.isBlank()) {
         traceId = TraceIdContext.getTraceId();
         spanId = TraceIdContext.getSpanId();
         sampled = TraceIdContext.isSampled();
      }

      String requestId = TraceIdContext.getRequestId();
      if (requestId == null || requestId.isBlank()) {
         requestId = traceId;
      }

      if (traceId != null && !traceId.isBlank()) {
         if (!request.getHeaders().containsHeader(TraceIdContext.TRACE_HEADER)) {
            request.getHeaders().add(TraceIdContext.TRACE_HEADER, traceId);
         }
         if (!request.getHeaders().containsHeader(TraceIdContext.REQUEST_ID_HEADER)) {
            request.getHeaders().add(TraceIdContext.REQUEST_ID_HEADER, requestId);
         }
         if (spanId != null && !request.getHeaders().containsHeader(TraceIdContext.SPAN_HEADER)) {
            request.getHeaders().add(TraceIdContext.SPAN_HEADER, spanId);
         }
         if (!request.getHeaders().containsHeader(TraceIdContext.TRACEPARENT_HEADER)) {
            String traceparent = TraceIdContext.formatTraceparent(traceId, spanId, sampled);
            request.getHeaders().add(TraceIdContext.TRACEPARENT_HEADER, traceparent);
         }
      }

      // 2. Business baggage context propagation
      UserContext userContext = UserContextHolder.getContext();
      Map<String, String> baggageItems = new LinkedHashMap<>();

      // Correlation ID propagation (distinct from traceId, tracking business flows)
      String correlationId = (userContext != null) ? userContext.getCorrelationId() : null;
      if (correlationId == null || correlationId.isBlank()) {
         correlationId = CorrelationIdContext.getCorrelationId();
      }
      if (correlationId == null || correlationId.isBlank()) {
         correlationId = CorrelationIdContext.generateCorrelationId();
      }

      if (!request.getHeaders().containsHeader(CorrelationIdContext.CORRELATION_ID_HEADER)) {
         request.getHeaders().add(CorrelationIdContext.CORRELATION_ID_HEADER, correlationId);
      }
      baggageItems.put(BaggageUtils.KEY_CORRELATION_ID, correlationId);

      if (userContext != null) {
         // Tenant ID
         if (userContext.getTenantId() != null && !userContext.getTenantId().isBlank()) {
            if (!request.getHeaders().containsHeader(UserContextFilter.TENANT_ID_HEADER)) {
               request.getHeaders().add(UserContextFilter.TENANT_ID_HEADER, userContext.getTenantId());
            }
            baggageItems.put(BaggageUtils.KEY_TENANT_ID, userContext.getTenantId());
         }

         // User ID
         if (userContext.getUserId() != null && !userContext.getUserId().isBlank()) {
            if (!request.getHeaders().containsHeader(UserContextFilter.USER_ID_HEADER)) {
               request.getHeaders().add(UserContextFilter.USER_ID_HEADER, userContext.getUserId());
            }
            baggageItems.put(BaggageUtils.KEY_USER_ID, userContext.getUserId());
         }

         // Session ID
         if (userContext.getSessionId() != null && !userContext.getSessionId().isBlank()) {
            if (!request.getHeaders().containsHeader(UserContextFilter.SESSION_ID_HEADER)) {
               request.getHeaders().add(UserContextFilter.SESSION_ID_HEADER, userContext.getSessionId());
            }
            baggageItems.put("session_id", userContext.getSessionId());
         }

         // Priority
         if (userContext.getPriority() != null && !userContext.getPriority().isBlank()) {
            if (!request.getHeaders().containsHeader(UserContextFilter.PRIORITY_HEADER)) {
               request.getHeaders().add(UserContextFilter.PRIORITY_HEADER, userContext.getPriority());
            }
            baggageItems.put(BaggageUtils.KEY_PRIORITY, userContext.getPriority());
         }

         // Feature Flags
         if (userContext.getFeatureFlags() != null && !userContext.getFeatureFlags().isEmpty()) {
            String flagsStr = String.join(",", userContext.getFeatureFlags());
            if (!request.getHeaders().containsHeader(UserContextFilter.FEATURE_FLAGS_HEADER)) {
               request.getHeaders().add(UserContextFilter.FEATURE_FLAGS_HEADER, flagsStr);
            }
            baggageItems.put(BaggageUtils.KEY_FEATURE_FLAGS, flagsStr);
         }
      }

      // 3. Inject W3C baggage header
      if (!baggageItems.isEmpty() && !request.getHeaders().containsHeader(BaggageUtils.BAGGAGE_HEADER)) {
         String baggageHeader = BaggageUtils.formatBaggage(baggageItems);
         if (!baggageHeader.isBlank()) {
            request.getHeaders().add(BaggageUtils.BAGGAGE_HEADER, baggageHeader);
         }
      }

      return execution.execute(request, body);
   }
}
