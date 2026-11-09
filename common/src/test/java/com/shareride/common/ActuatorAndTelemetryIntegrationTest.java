package com.shareride.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.probe.ServiceAvailabilityManager;
import com.shareride.common.probe.ShareRideAppHealthIndicator;
import com.shareride.common.probe.StartupProbeHealthIndicator;
import com.shareride.common.telemetry.TracePropagationInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
        classes = ActuatorAndTelemetryIntegrationTest.TestApplication.class,
        properties = {"shareride.redis.enabled=false", "management.health.rabbit.enabled=false"})
class ActuatorAndTelemetryIntegrationTest {

    @SpringBootApplication(
            scanBasePackages = "com.shareride.common",
            exclude = {
                org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class,
                org.springframework.boot.data.redis.autoconfigure
                        .DataRedisRepositoriesAutoConfiguration.class
            })
    static class TestApplication {}

    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired private ShareRideMetrics shareRideMetrics;

    @Autowired private ServiceAvailabilityManager availabilityManager;

    @Autowired private ShareRideAppHealthIndicator appHealthIndicator;

    @Autowired private StartupProbeHealthIndicator startupProbeHealthIndicator;

    @Autowired private TracePropagationInterceptor tracePropagationInterceptor;

    @Autowired private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("All metrics, probe, and telemetry beans must be auto-configured")
    void testBeansInjected() {
        assertNotNull(shareRideMetrics);
        assertNotNull(availabilityManager);
        assertNotNull(appHealthIndicator);
        assertNotNull(startupProbeHealthIndicator);
        assertNotNull(tracePropagationInterceptor);
        assertNotNull(meterRegistry);
        assertTrue(startupProbeHealthIndicator.isComplete());
    }

    @Test
    @DisplayName("Actuator health endpoint should return UP and expose custom ShareRide details")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator liveness, readiness, and startup probe endpoints must be available")
    void testProbesEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/startup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName(
            "Actuator prometheus scrape endpoint must be exposed and return valid metrics text"
                    + " format")
    void testPrometheusEndpoint() throws Exception {
        shareRideMetrics.incrementCounter("test.prometheus.metric", "env", "test");

        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }
}
