package com.shareride.common.logging.metrics;

import com.shareride.common.metrics.ShareRideMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance metrics collector and monitor for logging volume, level
 * distributions, queue depth,
 * serialization latency, and dropped logs.
 */
public final class LoggingMetrics {

   private static volatile ShareRideMetrics metricsFacade;
   private static final AtomicLong totalEvents = new AtomicLong();
   private static final AtomicLong totalDropped = new AtomicLong();
   private static final AtomicInteger queueDepth = new AtomicInteger();

   private LoggingMetrics() {
   }

   public static void setMetrics(ShareRideMetrics metrics) {
      metricsFacade = metrics;
      if (metrics != null) {
         metrics.registerGauge("logging.queue.depth", "Current queue depth of logging system",
               queueDepth, val -> val != null ? val.get() : 0);
      }
   }

   public static void setMeterRegistry(MeterRegistry meterRegistry) {
      if (meterRegistry != null) {
         setMetrics(new ShareRideMetrics(meterRegistry));
      }
   }

   public static void record(String level) {
      totalEvents.incrementAndGet();
      if (metricsFacade != null) {
         metricsFacade.recordLogEvent(level);
      }
   }

   public static void recordDropped(String reason) {
      totalDropped.incrementAndGet();
      if (metricsFacade != null) {
         metricsFacade.recordDroppedLog(reason);
      }
   }

   public static void recordSerializationTime(Duration duration) {
      if (metricsFacade != null && duration != null) {
         metricsFacade.recordLogSerializationTime(duration);
      }
   }

   public static void setQueueDepth(int depth) {
      queueDepth.set(Math.max(0, depth));
   }

   public static int getQueueDepth() {
      return queueDepth.get();
   }

   public static double getDropRate() {
      long total = totalEvents.get();
      long dropped = totalDropped.get();
      if (total + dropped == 0) {
         return 0.0;
      }
      return (double) dropped / (total + dropped) * 100.0;
   }

   public static long getTotalEvents() {
      return totalEvents.get();
   }

   public static long getTotalDropped() {
      return totalDropped.get();
   }

   public static void reset() {
      totalEvents.set(0);
      totalDropped.set(0);
      queueDepth.set(0);
   }
}
