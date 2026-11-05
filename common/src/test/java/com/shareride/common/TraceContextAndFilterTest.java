package com.shareride.common;

import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.filter.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextAndFilterTest {

    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdFilter();
        TraceIdContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Should generate W3C compliant 32-hex traceId and 16-hex spanId")
    void testTraceIdAndSpanIdGeneration() {
        String traceId = TraceIdContext.generateTraceId();
        String spanId = TraceIdContext.generateSpanId();

        assertNotNull(traceId);
        assertEquals(32, traceId.length(), "W3C traceId must be 32 hex chars (128-bit)");
        assertTrue(traceId.matches("^[0-9a-f]{32}$"));

        assertNotNull(spanId);
        assertEquals(16, spanId.length(), "W3C spanId must be 16 hex chars (64-bit)");
        assertTrue(spanId.matches("^[0-9a-f]{16}$"));
    }

    @Test
    @DisplayName("Should correctly format and parse W3C traceparent header")
    void testFormatAndParseTraceparent() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";

        String traceparent = TraceIdContext.formatTraceparent(traceId, spanId, true);
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", traceparent);

        TraceIdContext.TraceContextData parsed = TraceIdContext.parseTraceparent(traceparent);
        assertNotNull(parsed);
        assertEquals(traceId, parsed.traceId());
        assertEquals(spanId, parsed.parentSpanId());
        assertTrue(parsed.sampled());

        // Parse invalid or all-zero headers
        assertNull(TraceIdContext.parseTraceparent("invalid-header"));
        assertNull(TraceIdContext.parseTraceparent("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
        assertNull(TraceIdContext.parseTraceparent("00-00000000000000000000000000000000-00f067aa0ba902b7-01"));
    }

    @Test
    @DisplayName("TraceIdFilter should parse incoming W3C traceparent and inject headers + MDC")
    void testFilterWithTraceparent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides");
        String incomingTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        request.addHeader(TraceIdContext.TRACEPARENT_HEADER, incomingTraceparent);

        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] capturedTrace = new String[2];

        FilterChain filterChain = (req, res) -> {
            capturedTrace[0] = TraceIdContext.getTraceId();
            capturedTrace[1] = MDC.get(TraceIdFilter.MDC_TRACE_ID);
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", capturedTrace[0]);
            assertEquals(capturedTrace[0], capturedTrace[1]);
            assertNotNull(MDC.get(TraceIdFilter.MDC_SPAN_ID));
        };

        traceIdFilter.doFilter(request, response, filterChain);

        // Verification after filter completes
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", response.getHeader(TraceIdContext.TRACE_HEADER));
        assertNotNull(response.getHeader(TraceIdContext.SPAN_HEADER));
        assertTrue(response.getHeader(TraceIdContext.TRACEPARENT_HEADER).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"));

        // MDC & ThreadLocal must be cleared to prevent thread leak
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID));
        assertNull(MDC.get(TraceIdFilter.MDC_SPAN_ID));
    }

    @Test
    @DisplayName("TraceIdFilter should support legacy X-Trace-Id header and generate new IDs if absent")
    void testFilterWithLegacyHeadersAndFallback() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(TraceIdContext.TRACE_HEADER, "legacy-trace-12345");

        MockHttpServletResponse response = new MockHttpServletResponse();
        traceIdFilter.doFilter(request, response, new MockFilterChain());

        assertEquals("legacy-trace-12345", response.getHeader(TraceIdContext.TRACE_HEADER));
        assertNotNull(response.getHeader(TraceIdContext.SPAN_HEADER));
        assertNotNull(response.getHeader(TraceIdContext.TRACEPARENT_HEADER));

        // When no header is provided
        MockHttpServletRequest emptyReq = new MockHttpServletRequest("GET", "/api/v1/empty");
        MockHttpServletResponse emptyRes = new MockHttpServletResponse();
        traceIdFilter.doFilter(emptyReq, emptyRes, new MockFilterChain());

        String generatedTrace = emptyRes.getHeader(TraceIdContext.TRACE_HEADER);
        assertNotNull(generatedTrace);
        assertEquals(32, generatedTrace.length());
    }
}
