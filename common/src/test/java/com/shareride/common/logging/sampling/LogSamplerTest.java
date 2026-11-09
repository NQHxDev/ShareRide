package com.shareride.common.logging.sampling;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LogSamplerTest {

    @Test
    void testSlowOrErrorAlwaysSampled() {
        LogSampler sampler = new LogSampler(0.0, 0.0);
        assertTrue(sampler.shouldSample("INFO", true));
        assertTrue(sampler.shouldSample("DEBUG", true));
        assertTrue(sampler.shouldSample("ERROR", true));
        assertTrue(sampler.shouldSample("INFO", "trace-123", true));
    }

    @Test
    void testErrorAndWarnAlwaysRetainedByDefault() {
        LogSampler sampler = new LogSampler(0.5, 0.01);
        assertTrue(sampler.shouldSample("ERROR", false));
        assertTrue(sampler.shouldSample("WARN", false));
        assertTrue(sampler.shouldSample("ERROR", "trace-abc", false));
        assertTrue(sampler.shouldSample("WARN", "trace-abc", false));
    }

    @Test
    void testZeroSampleRateDropsNormalLogs() {
        LogSampler sampler = new LogSampler(0.0, 0.0);
        assertFalse(sampler.shouldSample("INFO", false));
        assertFalse(sampler.shouldSample("DEBUG", false));
        assertFalse(sampler.shouldSample("INFO", "trace-abc", false));
        assertFalse(sampler.shouldSample("DEBUG", "trace-abc", false));
    }

    @Test
    void testFullSampleRateKeepsAll() {
        LogSampler sampler = new LogSampler(1.0, 1.0);
        assertTrue(sampler.shouldSample("INFO", false));
        assertTrue(sampler.shouldSample("DEBUG", false));
        assertTrue(sampler.shouldSample("INFO", "trace-abc", false));
        assertTrue(sampler.shouldSample("DEBUG", "trace-abc", false));
    }

    @Test
    void testDeterministicTraceSampling() {
        LogSampler sampler = new LogSampler(0.5, 0.2);
        String traceId = "consistent-trace-id-xyz";

        boolean first = sampler.shouldSample("INFO", traceId, false);
        boolean second = sampler.shouldSample("INFO", traceId, false);
        assertEquals(
                first,
                second,
                "Deterministic sampling with the same traceId must produce identical results");
    }

    @Test
    void testSampleRateClamping() {
        LogSampler sampler = new LogSampler(-0.5, 1.5);
        assertEquals(0.0, sampler.getInfoSampleRate());
        assertEquals(1.0, sampler.getDebugSampleRate());

        sampler.setWarnSampleRate(2.0);
        assertEquals(1.0, sampler.getWarnSampleRate());

        sampler.setErrorSampleRate(-1.0);
        assertEquals(0.0, sampler.getErrorSampleRate());
    }
}
