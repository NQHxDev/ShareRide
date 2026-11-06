package com.shareride.common;

import com.shareride.common.metrics.MetricsAutoConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;

import static org.junit.jupiter.api.Assertions.*;

class MetricsAutoConfigurationTest {

   @Test
   @DisplayName("commonMeterRegistryCustomizer should attach standard application, env, version, instance tags")
   void testCommonTagsCustomizer() {
      MetricsAutoConfiguration config = new MetricsAutoConfiguration();
      MeterRegistryCustomizer<MeterRegistry> customizer = config.commonMeterRegistryCustomizer("auth-service",
            "staging", "2.1.0");

      MeterRegistry registry = new SimpleMeterRegistry();
      customizer.customize(registry);

      Counter counter = registry.counter("test.requests");
      counter.increment();

      assertEquals("auth-service", counter.getId().getTag("application"));
      assertEquals("staging", counter.getId().getTag("environment"));
      assertEquals("2.1.0", counter.getId().getTag("version"));
      assertNotNull(counter.getId().getTag("instance"));
   }
}
