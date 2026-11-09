package com.shareride.common.filter;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.metrics.ShareRideMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpLoggingFilterTest {

    private ShareRideLoggingProperties properties;
    private MeterRegistry meterRegistry;
    private ShareRideMetrics metrics;
    private HttpLoggingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ShareRideLoggingProperties();
        properties.setSlowThresholdMs(50); // low threshold for testing
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ShareRideMetrics(meterRegistry);
        filter = new HttpLoggingFilter(properties, Optional.of(metrics));
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should populate MDC httpMethod and httpUri during execution and clear after")
    void testMdcPopulatedAndCleared() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain filterChain =
                (req, res) -> {
                    chainExecuted.set(true);
                    assertEquals("GET", MDC.get(MdcConstants.HTTP_METHOD));
                    assertEquals("/api/v1/rides", MDC.get(MdcConstants.HTTP_URI));
                };

        filter.doFilter(request, response, filterChain);

        assertTrue(chainExecuted.get());
        assertNull(MDC.get(MdcConstants.HTTP_METHOD));
        assertNull(MDC.get(MdcConstants.HTTP_URI));
    }

    @Test
    @DisplayName("Should detect slow request and record slow request metric")
    void testSlowRequestDetection() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides/book");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        FilterChain slowChain =
                (req, res) -> {
                    try {
                        Thread.sleep(70); // longer than 50ms threshold
                    } catch (InterruptedException ignored) {
                    }
                };

        filter.doFilter(request, response, slowChain);

        // Verify slow request metric incremented
        double count =
                meterRegistry
                        .get("shareride.http.slow.requests")
                        .tag("method", "POST")
                        .tag("uri", "/api/v1/rides/book")
                        .tag("status", "200")
                        .counter()
                        .count();

        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("Should bypass excluded paths like /actuator/**")
    void testExcludedPaths() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain filterChain =
                (req, res) -> {
                    chainExecuted.set(true);
                    // Since shouldNotFilter is true, MDC should not be set by HttpLoggingFilter
                    assertNull(MDC.get(MdcConstants.HTTP_METHOD));
                };

        filter.doFilter(request, response, filterChain);
        assertTrue(chainExecuted.get());
    }

    @Test
    @DisplayName("Should bypass when logging is disabled via properties")
    void testDisabledLogging() throws ServletException, IOException {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain filterChain =
                (req, res) -> {
                    chainExecuted.set(true);
                    assertNull(MDC.get(MdcConstants.HTTP_METHOD));
                };

        filter.doFilter(request, response, filterChain);
        assertTrue(chainExecuted.get());
    }
}
