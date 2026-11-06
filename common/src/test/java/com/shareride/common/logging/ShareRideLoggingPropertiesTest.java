package com.shareride.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShareRideLoggingPropertiesTest {

   @Test
   @DisplayName("Verify default configuration properties for logging")
   void testDefaultProperties() {
      ShareRideLoggingProperties props = new ShareRideLoggingProperties();

      assertTrue(props.isEnabled());
      assertEquals(1000L, props.getSlowThresholdMs());
      assertFalse(props.isIncludeHeaders());
      assertTrue(props.getExcludePaths().contains("/actuator/**"));
      assertTrue(props.getSensitiveHeaders().contains("authorization"));

      props.setEnabled(false);
      props.setSlowThresholdMs(2000L);
      props.setIncludeHeaders(true);
      props.setExcludePaths(List.of("/health"));
      props.setSensitiveHeaders(Set.of("custom-token"));

      assertFalse(props.isEnabled());
      assertEquals(2000L, props.getSlowThresholdMs());
      assertTrue(props.isIncludeHeaders());
      assertEquals(1, props.getExcludePaths().size());
      assertTrue(props.getSensitiveHeaders().contains("custom-token"));
   }

}
