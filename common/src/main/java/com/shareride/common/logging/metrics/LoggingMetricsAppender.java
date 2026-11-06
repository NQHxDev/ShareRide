package com.shareride.common.logging.metrics;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback Appender that records real-time logging event metrics to Micrometer.
 */
public class LoggingMetricsAppender extends AppenderBase<ILoggingEvent> {

   @Override
   protected void append(ILoggingEvent eventObject) {
      if (eventObject != null && eventObject.getLevel() != null) {
         LoggingMetrics.record(eventObject.getLevel().toString());
      }
   }

}
