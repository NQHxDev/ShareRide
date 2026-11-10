package com.shareride.common.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdContextTest {

    @BeforeEach
    void setUp() {
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
    @DisplayName(
            "generateCorrelationId should generate standard UUID format and never collide with"
                    + " 32-hex traceId")
    void testGenerateCorrelationIdDistinctFromTraceId() {
        String correlationId = CorrelationIdContext.generateCorrelationId();
        String traceId = TraceIdContext.generateTraceId();

        assertNotNull(correlationId);
        assertNotNull(traceId);
        assertNotEquals(correlationId, traceId);

        // Correlation ID is a standard UUID with hyphens (36 chars)
        assertEquals(36, correlationId.length());
        assertDoesNotThrow(() -> UUID.fromString(correlationId));

        // Trace ID is 32 hex chars without hyphens
        assertEquals(32, traceId.length());
        assertTrue(traceId.matches("^[0-9a-f]{32}$"));
    }

    @Test
    @DisplayName("setCorrelationId should bind to thread-local and synchronize with SLF4J MDC")
    void testSetAndGetCorrelationIdSyncMdc() {
        String cid = "business-flow-" + UUID.randomUUID();
        CorrelationIdContext.setCorrelationId(cid);

        assertTrue(CorrelationIdContext.hasCorrelationId());
        assertEquals(cid, CorrelationIdContext.getCorrelationId());
        assertEquals(cid, MDC.get(MdcConstants.CORRELATION_ID));

        CorrelationIdContext.clear();
        assertFalse(CorrelationIdContext.hasCorrelationId());
        assertNull(CorrelationIdContext.getCorrelationId());
        assertNull(MDC.get(MdcConstants.CORRELATION_ID));
    }

    @Test
    @DisplayName(
            "getOrGenerate should return existing if present or generate and bind new if absent")
    void testGetOrGenerate() {
        assertFalse(CorrelationIdContext.hasCorrelationId());

        String generated = CorrelationIdContext.getOrGenerate();
        assertNotNull(generated);
        assertTrue(CorrelationIdContext.hasCorrelationId());
        assertEquals(generated, CorrelationIdContext.getCorrelationId());
        assertEquals(generated, MDC.get(MdcConstants.CORRELATION_ID));

        // Second call returns existing
        String existing = CorrelationIdContext.getOrGenerate();
        assertEquals(generated, existing);
    }

    @Test
    @DisplayName("CorrelationIdContext should maintain thread isolation")
    void testThreadIsolation() throws InterruptedException {
        CorrelationIdContext.setCorrelationId("main-thread-cid");

        AtomicReference<String> workerCid = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread =
                new Thread(
                        () -> {
                            workerCid.set(CorrelationIdContext.getCorrelationId());
                            latch.countDown();
                        });

        thread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertNull(workerCid.get(), "New thread must not inherit un-decorated ThreadLocal state");
        assertEquals("main-thread-cid", CorrelationIdContext.getCorrelationId());
    }
}
