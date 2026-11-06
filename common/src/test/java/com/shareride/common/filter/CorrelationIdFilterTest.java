package com.shareride.common.filter;

import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.logging.MdcConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

   private CorrelationIdFilter filter;

   @BeforeEach
   void setUp() {
      filter = new CorrelationIdFilter();
      CorrelationIdContext.clear();
      TraceIdContext.clear();
      MDC.clear();
   }

   @AfterEach
   void tearDown() {
      CorrelationIdContext.clear();
      TraceIdContext.clear();
      MDC.clear();
   }

   @Test
   @DisplayName("Should extract existing X-Correlation-Id header, populate context & MDC, and return in response")
   void testFilterWithExplicitHeader() throws ServletException, IOException {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides/book");
      String explicitCid = "ride-booking-flow-8899";
      request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, explicitCid);

      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean executed = new AtomicBoolean(false);

      FilterChain filterChain = (req, res) -> {
         executed.set(true);
         assertEquals(explicitCid, CorrelationIdContext.getCorrelationId());
         assertEquals(explicitCid, MDC.get(MdcConstants.CORRELATION_ID));
      };

      filter.doFilter(request, response, filterChain);

      assertTrue(executed.get());
      assertEquals(explicitCid, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));

      // Must be cleared after request ends
      assertNull(CorrelationIdContext.getCorrelationId());
      assertNull(MDC.get(MdcConstants.CORRELATION_ID));
   }

   @Test
   @DisplayName("Should extract correlationId from W3C Baggage header when X-Correlation-Id is absent")
   void testFilterWithBaggageHeader() throws ServletException, IOException {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides/123");
      request.addHeader("baggage", "userId=u123,correlationId=baggage-flow-999,priority=HIGH");

      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean executed = new AtomicBoolean(false);

      FilterChain filterChain = (req, res) -> {
         executed.set(true);
         assertEquals("baggage-flow-999", CorrelationIdContext.getCorrelationId());
         assertEquals("baggage-flow-999", MDC.get(MdcConstants.CORRELATION_ID));
      };

      filter.doFilter(request, response, filterChain);

      assertTrue(executed.get());
      assertEquals("baggage-flow-999", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
   }

   @Test
   @DisplayName("Should generate a fresh unique correlation ID distinct from traceId when absent")
   void testFilterGeneratesDistinctFromTraceId() throws ServletException, IOException {
      // Simulate TraceIdFilter having run upstream
      String technicalTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
      TraceIdContext.setTraceId(technicalTraceId);
      MDC.put(MdcConstants.TRACE_ID, technicalTraceId);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean executed = new AtomicBoolean(false);

      FilterChain filterChain = (req, res) -> {
         executed.set(true);
         String capturedCorrelationId = CorrelationIdContext.getCorrelationId();

         assertNotNull(capturedCorrelationId);
         // Crucial: Correlation ID must NEVER equal technical trace ID!
         assertNotEquals(technicalTraceId, capturedCorrelationId);
         // Must be a valid UUID format
         assertDoesNotThrow(() -> UUID.fromString(capturedCorrelationId));

         assertEquals(capturedCorrelationId, MDC.get(MdcConstants.CORRELATION_ID));
         assertEquals(technicalTraceId, MDC.get(MdcConstants.TRACE_ID));
      };

      filter.doFilter(request, response, filterChain);

      assertTrue(executed.get());
      String responseCid = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
      assertNotNull(responseCid);
      assertNotEquals(technicalTraceId, responseCid);
      assertDoesNotThrow(() -> UUID.fromString(responseCid));
   }
}
