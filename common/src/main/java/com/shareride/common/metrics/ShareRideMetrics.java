package com.shareride.common.metrics;

import io.micrometer.core.instrument.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Enterprise metrics facade for recording microservice business metrics,
 * counters, execution latency timers, distribution summaries, SRE SLO/SLI
 * tracking,
 * resilience metrics, and security observability events.
 */
public class ShareRideMetrics {

   private static final String DEFAULT_PREFIX = "shareride.";

   private final MeterRegistry meterRegistry;
   private final Map<String, AtomicInteger> activeDriversGauges = new ConcurrentHashMap<>();
   private final Map<String, AtomicReference<Double>> errorBudgetGauges = new ConcurrentHashMap<>();
   private final Map<String, AtomicInteger> circuitBreakerStateGauges = new ConcurrentHashMap<>();

   public ShareRideMetrics(MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
   }

   public MeterRegistry getMeterRegistry() {
      return meterRegistry;
   }

   // ==========================================
   // Core Micrometer Facade
   // ==========================================

   public void incrementCounter(String name, String... tags) {
      incrementCounter(name, 1.0, tags);
   }

   public void incrementCounter(String name, double amount, String... tags) {
      Counter.builder(resolveMetricName(name))
            .tags(parseTags(tags))
            .register(meterRegistry)
            .increment(amount);
   }

   public void recordException(String code, String exceptionType) {
      incrementCounter("errors.total",
            "code", code != null ? code : "unknown",
            "exception", exceptionType != null ? exceptionType : "unknown");
   }

   public void recordLogEvent(String level) {
      incrementCounter("logging.events", "level", level != null ? level.toUpperCase() : "UNKNOWN");
   }

   public void recordDroppedLog(String reason) {
      incrementCounter("logging.dropped", "reason", reason != null ? reason : "queue_full");
   }

   public void recordLogSerializationTime(Duration duration) {
      recordTime("logging.serialization.duration", duration);
   }

   public void recordSlowRequest(String method, String uri, int status) {
      incrementCounter("http.slow.requests",
            "method", method != null ? method : "UNKNOWN",
            "uri", uri != null ? uri : "/",
            "status", String.valueOf(status));
   }

   public void recordHttpRequest(String method, String uri, int status, Duration duration) {
      recordTime("http.requests.duration", duration,
            "method", method != null ? method : "UNKNOWN",
            "uri", uri != null ? uri : "/",
            "status", String.valueOf(status));
   }

   public void recordTime(String name, Duration duration, String... tags) {
      Timer.builder(resolveMetricName(name))
            .tags(parseTags(tags))
            .register(meterRegistry)
            .record(duration);
   }

   public void recordTime(String name, long amount, TimeUnit unit, String... tags) {
      Timer.builder(resolveMetricName(name))
            .tags(parseTags(tags))
            .register(meterRegistry)
            .record(amount, unit);
   }

   public <T> T time(String name, Supplier<T> supplier, String... tags) {
      Timer timer = Timer.builder(resolveMetricName(name))
            .tags(parseTags(tags))
            .register(meterRegistry);
      return timer.record(supplier);
   }

   public void time(String name, Runnable runnable, String... tags) {
      Timer timer = Timer.builder(resolveMetricName(name))
            .tags(parseTags(tags))
            .register(meterRegistry);
      timer.record(runnable);
   }

   public Timer.Sample startTimer() {
      return Timer.start(meterRegistry);
   }

   public void stopTimer(Timer.Sample sample, String name, String... tags) {
      if (sample != null) {
         Timer timer = Timer.builder(resolveMetricName(name))
               .tags(parseTags(tags))
               .register(meterRegistry);
         sample.stop(timer);
      }
   }

   public void recordSummary(String name, double amount, String... tags) {
      DistributionSummary.builder(resolveMetricName(name))
            .tags(parseTags(tags))
            .register(meterRegistry)
            .record(amount);
   }

   public <T> T registerGauge(String name, String description, T stateObject,
         ToDoubleFunction<T> valueFunction, String... tags) {
      Gauge.builder(resolveMetricName(name), stateObject, valueFunction)
            .description(description)
            .tags(parseTags(tags))
            .register(meterRegistry);
      return stateObject;
   }

   public AtomicInteger registerAtomicInteger(String name, String description,
         AtomicInteger atomicInteger, String... tags) {
      Gauge.builder(resolveMetricName(name), atomicInteger, AtomicInteger::get)
            .description(description)
            .tags(parseTags(tags))
            .register(meterRegistry);
      return atomicInteger;
   }

   public AtomicLong registerAtomicLong(String name, String description,
         AtomicLong atomicLong, String... tags) {
      Gauge.builder(resolveMetricName(name), atomicLong, AtomicLong::get)
            .description(description)
            .tags(parseTags(tags))
            .register(meterRegistry);
      return atomicLong;
   }

   // ==========================================
   // 2. SLO / SLI & Saturation Tracking
   // ==========================================

   /**
    * Record database query duration, status, and table for SRE latency
    * percentiles.
    */
   public void recordDatabaseQuery(String database, String table, Duration duration, boolean success) {
      String status = success ? "SUCCESS" : "FAILURE";
      recordTime("database.query.duration", duration,
            "database", database != null ? database : "default",
            "table", table != null ? table : "unknown",
            "status", status);
      incrementCounter("database.query.total",
            "database", database != null ? database : "default",
            "table", table != null ? table : "unknown",
            "status", status);
   }

   /**
    * Track connection pool saturation (active, idle, max).
    */
   public void recordConnectionPoolStats(String poolName, int active, int idle, int max) {
      String base = "pool." + (poolName != null ? poolName : "default");
      Gauge.builder(resolveMetricName(base + ".active"), () -> active).register(meterRegistry);
      Gauge.builder(resolveMetricName(base + ".idle"), () -> idle).register(meterRegistry);
      Gauge.builder(resolveMetricName(base + ".max"), () -> max).register(meterRegistry);
      if (max > 0) {
         double saturation = (double) active / max * 100.0;
         Gauge.builder(resolveMetricName(base + ".saturation.percent"), () -> saturation).register(meterRegistry);
      }
   }

   /**
    * Record circuit breaker state transitions (0 = CLOSED, 1 = HALF_OPEN, 2 =
    * OPEN).
    */
   public void recordCircuitBreakerState(String name, String state) {
      int stateCode = "OPEN".equalsIgnoreCase(state) ? 2 : ("HALF_OPEN".equalsIgnoreCase(state) ? 1 : 0);
      circuitBreakerStateGauges.computeIfAbsent(name, n -> {
         AtomicInteger gaugeVal = new AtomicInteger(stateCode);
         Gauge.builder(resolveMetricName("resilience.circuitbreaker.state"), gaugeVal, AtomicInteger::get)
               .tag("name", n)
               .description("Circuit Breaker State: 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
               .register(meterRegistry);
         return gaugeVal;
      }).set(stateCode);
   }

   /**
    * Record circuit breaker call outcome (success, failure, short_circuited,
    * fallback).
    */
   public void recordCircuitBreakerCall(String name, String outcome) {
      incrementCounter("resilience.circuitbreaker.calls",
            "name", name != null ? name : "default",
            "outcome", outcome != null ? outcome : "unknown");
   }

   /**
    * Record retry attempts and outcome.
    */
   public void recordRetry(String operation, int attempt, boolean success) {
      incrementCounter("resilience.retry.total",
            "operation", operation != null ? operation : "unknown",
            "attempt", String.valueOf(attempt),
            "success", String.valueOf(success));
   }

   /**
    * Record resilience fallback executions.
    */
   public void recordFallback(String operation, String reason) {
      incrementCounter("resilience.fallback.total",
            "operation", operation != null ? operation : "unknown",
            "reason", reason != null ? reason : "unknown");
   }

   /**
    * Record Error Budget consumption and remaining budget percentage for SLA.
    * E.g. targetSla = 99.9 (0.1% allowed error rate), currentAvailability = 99.85
    * -> remaining budget = 50%
    */
   public void recordErrorBudget(String service, double targetSla, double currentAvailability) {
      double maxErrorRate = 100.0 - targetSla;
      double currentErrorRate = Math.max(0.0, 100.0 - currentAvailability);
      double remainingPercent = (maxErrorRate > 0)
            ? Math.max(0.0, (maxErrorRate - currentErrorRate) / maxErrorRate * 100.0)
            : 100.0;

      errorBudgetGauges.computeIfAbsent(service, s -> {
         AtomicReference<Double> ref = new AtomicReference<>(remainingPercent);
         Gauge.builder(resolveMetricName("slo.error_budget.remaining_percent"), ref, AtomicReference::get)
               .tag("service", s)
               .description("Remaining Error Budget percentage based on SLA target")
               .register(meterRegistry);
         return ref;
      }).set(remainingPercent);
   }

   /**
    * Monitor ThreadPool saturation (active, queued, max pool size).
    */
   public void registerThreadPoolMetrics(String poolName, ThreadPoolExecutor executor) {
      if (executor == null)
         return;
      String prefix = "threadpool." + (poolName != null ? poolName : "custom");
      registerGauge(prefix + ".active.threads", "Active executing worker threads",
            executor, ThreadPoolExecutor::getActiveCount);
      registerGauge(prefix + ".pool.size", "Current thread pool size",
            executor, ThreadPoolExecutor::getPoolSize);
      registerGauge(prefix + ".queue.size", "Queued tasks awaiting worker thread",
            executor, e -> e.getQueue().size());
      registerGauge(prefix + ".completed.tasks", "Total completed tasks",
            executor, ThreadPoolExecutor::getCompletedTaskCount);
   }

   // ==========================================
   // 6. Business Metrics (ShareRide Domain)
   // ==========================================

   /**
    * Record ride booking requested.
    */
   public void recordRideBookingRequested(String city, String vehicleType) {
      incrementCounter("ride.booking.requested",
            "city", city != null ? city.toLowerCase() : "unknown",
            "vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard");
   }

   /**
    * Record ride booking outcome (SUCCESS, CANCELLED_BY_PASSENGER,
    * CANCELLED_BY_DRIVER, NO_DRIVER_FOUND, FAILED).
    */
   public void recordRideBookingOutcome(String status, String city, String vehicleType, String reason) {
      incrementCounter("ride.booking.outcome",
            "status", status != null ? status : "UNKNOWN",
            "city", city != null ? city.toLowerCase() : "unknown",
            "vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard",
            "reason", reason != null ? reason : "none");
   }

   /**
    * Record completed trip metrics (duration and distance in km).
    */
   public void recordRideTripCompleted(String city, String vehicleType, Duration duration, double distanceKm) {
      recordTime("ride.trip.duration", duration,
            "city", city != null ? city.toLowerCase() : "unknown",
            "vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard");
      recordSummary("ride.trip.distance.km", distanceKm,
            "city", city != null ? city.toLowerCase() : "unknown",
            "vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard");
      incrementCounter("ride.trip.completed",
            "city", city != null ? city.toLowerCase() : "unknown",
            "vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard");
   }

   /**
    * Set active drivers count by city and vehicle type.
    */
   public void setActiveDrivers(String city, String vehicleType, int count) {
      String key = (city != null ? city.toLowerCase() : "unknown") + ":" +
            (vehicleType != null ? vehicleType.toLowerCase() : "standard");
      activeDriversGauges.computeIfAbsent(key, k -> {
         AtomicInteger gaugeVal = new AtomicInteger(count);
         Gauge.builder(resolveMetricName("driver.active.count"), gaugeVal, AtomicInteger::get)
               .tag("city", city != null ? city.toLowerCase() : "unknown")
               .tag("vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard")
               .description("Currently active and online drivers")
               .register(meterRegistry);
         return gaugeVal;
      }).set(count);
   }

   /**
    * Record passenger pickup wait time distribution.
    */
   public void recordPassengerWaitTime(Duration waitTime, String city, String vehicleType) {
      recordTime("ride.passenger.wait_time", waitTime,
            "city", city != null ? city.toLowerCase() : "unknown",
            "vehicle_type", vehicleType != null ? vehicleType.toLowerCase() : "standard");
   }

   /**
    * Record dynamic pricing engine calculation duration and surge multiplier tier.
    */
   public void recordPricingCalculationTime(Duration calculationTime, String city, String surgeTier) {
      recordTime("pricing.calculation.duration", calculationTime,
            "city", city != null ? city.toLowerCase() : "unknown",
            "surge_tier", surgeTier != null ? surgeTier : "1.0x");
   }

   /**
    * Record payment transaction outcome, gateway, method, and amount.
    */
   public void recordPaymentTransaction(String gateway, String paymentMethod, String status, double amount,
         String currency) {
      incrementCounter("payment.transaction.total",
            "gateway", gateway != null ? gateway.toLowerCase() : "unknown",
            "method", paymentMethod != null ? paymentMethod.toLowerCase() : "unknown",
            "status", status != null ? status : "UNKNOWN",
            "currency", currency != null ? currency.toUpperCase() : "VND");
      recordSummary("payment.transaction.amount", amount,
            "gateway", gateway != null ? gateway.toLowerCase() : "unknown",
            "status", status != null ? status : "UNKNOWN",
            "currency", currency != null ? currency.toUpperCase() : "VND");
   }

   /**
    * Track user signup funnel conversion steps
    * (e.g. INITIATED, PHONE_VERIFIED, PROFILE_COMPLETED, DOCUMENT_UPLOADED,
    * APPROVED).
    */
   public void recordUserSignupStep(String step, String status, String channel, String userType) {
      incrementCounter("user.signup.funnel",
            "step", step != null ? step.toUpperCase() : "UNKNOWN",
            "status", status != null ? status : "SUCCESS",
            "channel", channel != null ? channel.toLowerCase() : "organic",
            "user_type", userType != null ? userType.toLowerCase() : "passenger");
   }

   // ==========================================
   // 7. Security Observability Events
   // ==========================================

   /**
    * Record authentication failure (e.g. invalid credentials, expired token,
    * signature mismatch).
    */
   public void recordAuthFailure(String authType, String reason, String clientIpPrefix) {
      incrementCounter("security.auth.failure",
            "auth_type", authType != null ? authType : "jwt",
            "reason", reason != null ? reason : "invalid_credentials",
            "client_ip_prefix", sanitizeIpPrefix(clientIpPrefix));
   }

   /**
    * Record successful authentication.
    */
   public void recordAuthSuccess(String authType, String role) {
      incrementCounter("security.auth.success",
            "auth_type", authType != null ? authType : "jwt",
            "role", role != null ? role : "user");
   }

   /**
    * Record authorization failure (HTTP 403 Forbidden).
    */
   public void recordAccessDenied(String resource, String requiredRole, String action) {
      incrementCounter("security.access.denied",
            "resource", resource != null ? resource : "unknown",
            "required_role", requiredRole != null ? requiredRole : "unknown",
            "action", action != null ? action : "read");
   }

   /**
    * Record rate limiting trigger event (HTTP 429 Too Many Requests).
    */
   public void recordRateLimitExceeded(String clientIpPrefix, String endpoint, String limitType) {
      incrementCounter("security.ratelimit.exceeded",
            "endpoint", endpoint != null ? endpoint : "unknown",
            "limit_type", limitType != null ? limitType : "ip",
            "client_ip_prefix", sanitizeIpPrefix(clientIpPrefix));
   }

   /**
    * Record suspicious activity detection (SQL injection attempt, credential
    * stuffing, brute force).
    */
   public void recordSuspiciousActivity(String activityType, String clientIpPrefix, String severity) {
      incrementCounter("security.suspicious.activity",
            "activity_type", activityType != null ? activityType : "unknown",
            "severity", severity != null ? severity.toUpperCase() : "MEDIUM",
            "client_ip_prefix", sanitizeIpPrefix(clientIpPrefix));
   }

   /**
    * Track API Key usage and validity.
    */
   public void recordApiKeyUsage(String apiKeyId, String clientName, String endpoint, boolean valid) {
      incrementCounter("security.apikey.usage",
            "api_key_id", apiKeyId != null ? apiKeyId : "unknown",
            "client_name", clientName != null ? clientName : "unknown",
            "endpoint", endpoint != null ? endpoint : "unknown",
            "valid", String.valueOf(valid));
   }

   private String sanitizeIpPrefix(String ip) {
      if (ip == null || ip.isBlank()) {
         return "unknown";
      }
      int lastDot = ip.lastIndexOf('.');
      if (lastDot > 0) {
         return ip.substring(0, lastDot) + ".0/24";
      }
      return ip;
   }

   private String resolveMetricName(String name) {
      if (name == null || name.isBlank()) {
         return DEFAULT_PREFIX + "custom";
      }
      if (name.startsWith(DEFAULT_PREFIX)) {
         return name;
      }
      return DEFAULT_PREFIX + name;
   }

   private Tags parseTags(String... keyValues) {
      if (keyValues == null || keyValues.length == 0) {
         return Tags.empty();
      }
      List<Tag> tagList = new ArrayList<>();
      for (int i = 0; i < keyValues.length; i += 2) {
         String key = keyValues[i];
         String value = (i + 1 < keyValues.length) ? keyValues[i + 1] : "unknown";
         if (key != null && !key.isBlank()) {
            tagList.add(Tag.of(key, value != null ? value : ""));
         }
      }
      return Tags.of(tagList);
   }
}
