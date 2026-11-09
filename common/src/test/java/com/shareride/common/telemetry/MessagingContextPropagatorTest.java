package com.shareride.common.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MessagingContextPropagatorTest {

    @BeforeEach
    void setUp() {
        MessagingContextPropagator.clear();
    }

    @AfterEach
    void tearDown() {
        MessagingContextPropagator.clear();
    }

    @Test
    void testInjectHeaders() {
        TraceIdContext.setTraceId("4bf92f3577b34da6a3ce929d0e0e4736");
        TraceIdContext.setSpanId("00f067aa0ba902b7");
        TraceIdContext.setSampled(true);

        UserContext user =
                UserContext.builder()
                        .userId("usr-mq-1")
                        .tenantId("tenant-sg")
                        .correlationId("corr-123")
                        .priority("HIGH")
                        .featureFlags(Set.of("feature-mq", "beta"))
                        .build();
        UserContextHolder.setContext(user);

        Map<String, Object> headers = new HashMap<>();
        MessagingContextPropagator.inject(headers);

        assertEquals(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                headers.get(MessagingContextPropagator.TRACE_ID_HEADER));
        assertEquals("00f067aa0ba902b7", headers.get(MessagingContextPropagator.SPAN_ID_HEADER));
        assertEquals("usr-mq-1", headers.get(MessagingContextPropagator.USER_ID_HEADER));
        assertEquals("tenant-sg", headers.get(MessagingContextPropagator.TENANT_ID_HEADER));
        assertEquals("corr-123", headers.get(MessagingContextPropagator.CORRELATION_ID_HEADER));
        assertEquals("HIGH", headers.get(MessagingContextPropagator.PRIORITY_HEADER));
        assertTrue(
                headers.get(MessagingContextPropagator.TRACEPARENT_HEADER)
                        .toString()
                        .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"));
    }

    @Test
    void testExtractHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(
                MessagingContextPropagator.TRACEPARENT_HEADER,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        headers.put(
                MessagingContextPropagator.USER_ID_HEADER,
                "consumer-user".getBytes(StandardCharsets.UTF_8));
        headers.put(MessagingContextPropagator.TENANT_ID_HEADER, "tenant-consumer");
        headers.put(MessagingContextPropagator.CORRELATION_ID_HEADER, "corr-custom");

        MessagingContextPropagator.extract(headers);

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", TraceIdContext.getTraceId());
        assertEquals("00f067aa0ba902b7", TraceIdContext.getParentId());

        UserContext user = UserContextHolder.getContext();
        assertEquals("consumer-user", user.getUserId());
        assertEquals("tenant-consumer", user.getTenantId());
        assertEquals("corr-custom", user.getCorrelationId());

        // SLF4J MDC check
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", MDC.get(MdcConstants.TRACE_ID));
        assertEquals("consumer-user", MDC.get(MdcConstants.USER_ID));
        assertEquals("tenant-consumer", MDC.get(MdcConstants.TENANT_ID));
        assertEquals("corr-custom", MDC.get(MdcConstants.CORRELATION_ID));
    }

    @Test
    void testClear() {
        TraceIdContext.setTraceId("trace-1");
        UserContextHolder.setContext(UserContext.builder().userId("user-1").build());
        MDC.put(MdcConstants.TRACE_ID, "trace-1");
        MDC.put(MdcConstants.USER_ID, "user-1");

        MessagingContextPropagator.clear();

        assertFalse(TraceIdContext.hasTraceId());
        assertNull(TraceIdContext.getRawTraceId());
        assertFalse(UserContextHolder.hasContext());
        assertNull(UserContextHolder.getRawContext());
        assertFalse(UserContextHolder.isAuthenticated());
        assertNull(MDC.get(MdcConstants.TRACE_ID));
        assertNull(MDC.get(MdcConstants.USER_ID));
    }
}
