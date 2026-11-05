package com.shareride.common.logging.metrics;

import com.shareride.common.metrics.ShareRideMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ShareRideMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ShareRideMetrics(meterRegistry);
        LoggingMetrics.setMetrics(metrics);
        LoggingMetrics.reset();
    }

    @AfterEach
    void tearDown() {
        LoggingMetrics.reset();
    }

    @Test
    void testRecordMetrics() {
        LoggingMetrics.record("INFO");
        LoggingMetrics.record("ERROR");
        LoggingMetrics.record("INFO");

        assertEquals(3, LoggingMetrics.getTotalEvents());

        double infoCount = meterRegistry.get("shareride.logging.events")
                .tag("level", "INFO")
                .counter()
                .count();
        assertEquals(2.0, infoCount);

        double errorCount = meterRegistry.get("shareride.logging.events")
                .tag("level", "ERROR")
                .counter()
                .count();
        assertEquals(1.0, errorCount);
    }

    @Test
    void testRecordDropped() {
        LoggingMetrics.recordDropped("queue_full");
        LoggingMetrics.recordDropped("queue_full");

        assertEquals(2, LoggingMetrics.getTotalDropped());

        double droppedCount = meterRegistry.get("shareride.logging.dropped")
                .tag("reason", "queue_full")
                .counter()
                .count();
        assertEquals(2.0, droppedCount);
    }
}
