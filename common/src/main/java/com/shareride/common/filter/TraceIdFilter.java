package com.shareride.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to capture or generate traceId and attach to MDC, request scope, and
 * response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

   private static final String MDC_KEY = "traceId";

   @Override
   protected void doFilterInternal(HttpServletRequest request,
         HttpServletResponse response,
         FilterChain filterChain) throws ServletException, IOException {
      String traceId = request.getHeader(TraceIdContext.TRACE_HEADER);
      if (traceId == null || traceId.isBlank()) {
         traceId = request.getHeader(TraceIdContext.REQUEST_ID_HEADER);
      }
      if (traceId == null || traceId.isBlank()) {
         traceId = TraceIdContext.generateTraceId();
      }

      TraceIdContext.setTraceId(traceId);
      MDC.put(MDC_KEY, traceId);
      response.setHeader(TraceIdContext.TRACE_HEADER, traceId);

      try {
         filterChain.doFilter(request, response);
      } finally {
         MDC.remove(MDC_KEY);
         TraceIdContext.clear();
      }
   }
}
