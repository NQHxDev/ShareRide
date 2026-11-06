package com.shareride.common.filter;

import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.telemetry.BaggageUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Servlet filter that captures or establishes an end-to-end business Request Correlation ID.
 * <p>
 * Ensures business flow correlation is distinct from technical distributed Trace IDs (TraceIdFilter).
 * Populates SLF4J MDC, establishes {@link CorrelationIdContext}, and returns {@code X-Correlation-Id}
 * on downstream HTTP response headers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class CorrelationIdFilter extends OncePerRequestFilter {

   public static final String CORRELATION_ID_HEADER = CorrelationIdContext.CORRELATION_ID_HEADER;

   @Override
   protected void doFilterInternal(HttpServletRequest request,
         HttpServletResponse response,
         FilterChain filterChain) throws ServletException, IOException {

      // 1. Try X-Correlation-Id header
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);

      // 2. Fallback to W3C Baggage header (baggage: correlationId=...)
      if (correlationId == null || correlationId.isBlank()) {
         String rawBaggage = request.getHeader(BaggageUtils.BAGGAGE_HEADER);
         if (rawBaggage != null && !rawBaggage.isBlank()) {
            Map<String, String> baggageMap = BaggageUtils.parseBaggage(rawBaggage);
            correlationId = baggageMap.get(BaggageUtils.KEY_CORRELATION_ID);
         }
      }

      // 3. Fallback to existing CorrelationIdContext (if already established upstream)
      if ((correlationId == null || correlationId.isBlank()) && CorrelationIdContext.hasCorrelationId()) {
         correlationId = CorrelationIdContext.getCorrelationId();
      }

      // 4. Generate a new, unique Correlation ID distinct from traceId
      if (correlationId == null || correlationId.isBlank()) {
         correlationId = CorrelationIdContext.generateCorrelationId();
      } else {
         correlationId = correlationId.trim();
      }

      // 5. Bind to ThreadLocal context & SLF4J MDC
      CorrelationIdContext.setCorrelationId(correlationId);

      // 6. Echo back in HTTP response headers for caller traceability
      response.setHeader(CORRELATION_ID_HEADER, correlationId);

      try {
         filterChain.doFilter(request, response);
      } finally {
         CorrelationIdContext.clear();
      }
   }
}
