package com.shareride.common.telemetry;

import static com.shareride.common.telemetry.AdaptiveTraceSampler.nonNull;
import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptiveTraceSamplerTest {

    @Test
    @DisplayName("Should never sample actuator or health probe endpoints")
    void testActuatorEndpointsExcluded() {
        AdaptiveTraceSampler sampler = new AdaptiveTraceSampler(1.0); // 100% sampling rate

        assertFalse(
                sampler.shouldSample(
                        "/actuator/health", false, "4bf92f3577b34da6a3ce929d0e0e4736"));
        assertFalse(
                sampler.shouldSample(
                        "/actuator/prometheus", false, "4bf92f3577b34da6a3ce929d0e0e4736"));
        assertFalse(sampler.shouldSample("/health", false, "4bf92f3577b34da6a3ce929d0e0e4736"));
        assertFalse(sampler.shouldSample("/livez", false, "4bf92f3577b34da6a3ce929d0e0e4736"));

        SamplingResult result =
                sampler.shouldSample(
                        nonNull(Context.root()),
                        "4bf92f3577b34da6a3ce929d0e0e4736",
                        "/actuator/metrics",
                        SpanKind.SERVER,
                        nonNull(Attributes.empty()),
                        nonNull(Collections.emptyList()));
        assertEquals(SamplingDecision.DROP, result.getDecision());
    }

    @Test
    @DisplayName("Should always sample high-priority requests even at 0% sampling rate")
    void testHighPriorityAlwaysSampled() {
        AdaptiveTraceSampler sampler = new AdaptiveTraceSampler(0.0); // 0% baseline rate

        // With highPriority flag
        assertTrue(
                sampler.shouldSample(
                        "/api/v1/rides/book", true, "4bf92f3577b34da6a3ce929d0e0e4736"));

        // OpenTelemetry attributes with priority=HIGH
        Attributes attrs = Attributes.builder().put("priority", "HIGH").build();
        SamplingResult result =
                sampler.shouldSample(
                        nonNull(Context.root()),
                        "4bf92f3577b34da6a3ce929d0e0e4736",
                        "HTTP POST /api/v1/rides/book",
                        SpanKind.SERVER,
                        nonNull(attrs),
                        nonNull(Collections.emptyList()));
        assertEquals(SamplingDecision.RECORD_AND_SAMPLE, result.getDecision());
    }

    @Test
    @DisplayName("Should respect 100% sampling rate for normal requests")
    void testFullSamplingRate() {
        AdaptiveTraceSampler sampler = new AdaptiveTraceSampler(1.0);
        assertTrue(
                sampler.shouldSample(
                        "/api/v1/rides/123", false, "4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    @DisplayName("Should drop normal requests when probability is 0.0")
    void testZeroSamplingRate() {
        AdaptiveTraceSampler sampler = new AdaptiveTraceSampler(0.0);
        assertFalse(
                sampler.shouldSample(
                        "/api/v1/rides/123", false, "4bf92f3577b34da6a3ce929d0e0e4736"));
    }
}
