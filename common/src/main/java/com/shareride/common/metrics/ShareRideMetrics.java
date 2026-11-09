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
 * Enterprise metrics facade for recording microservice business metrics, counters, execution
 * latency timers, distribution summaries, SRE SLO/SLI tracking, resilience metrics, and security
 * observability events.
 */
public class ShareRideMetrics {

    private static final String DEFAULT_PREFIX = "shareride.";

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicInteger> activeDriversGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> errorBudgetGauges =
            new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> circuitBreakerStateGauges = new ConcurrentHashMap<>();

    public ShareRideMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    // Core Micrometer Facade

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
        incrementCounter(
                "errors.total",
                "code",
                code != null ? code : "unknown",
                "exception",
                exceptionType != null ? exceptionType : "unknown");
    }

    public void recordLogEvent(String level) {
        incrementCounter(
                "logging.events", "level", level != null ? level.toUpperCase() : "UNKNOWN");
    }

    public void recordDroppedLog(String reason) {
        incrementCounter("logging.dropped", "reason", reason != null ? reason : "queue_full");
    }

    public void recordLogSerializationTime(Duration duration) {
        recordTime("logging.serialization.duration", duration);
    }

    public void recordSlowRequest(String method, String uri, int status) {
        incrementCounter(
                "http.slow.requests",
                "method",
                method != null ? method : "UNKNOWN",
                "uri",
                uri != null ? uri : "/",
                "status",
                String.valueOf(status));
    }

    public void recordHttpRequest(String method, String uri, int status, Duration duration) {
        recordTime(
                "http.requests.duration",
                duration,
                "method",
                method != null ? method : "UNKNOWN",
                "uri",
                uri != null ? uri : "/",
                "status",
                String.valueOf(status));
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
        Timer timer =
                Timer.builder(resolveMetricName(name))
                        .tags(parseTags(tags))
                        .register(meterRegistry);
        return timer.record(supplier);
    }

    public void time(String name, Runnable runnable, String... tags) {
        Timer timer =
                Timer.builder(resolveMetricName(name))
                        .tags(parseTags(tags))
                        .register(meterRegistry);
        timer.record(runnable);
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        if (sample != null) {
            Timer timer =
                    Timer.builder(resolveMetricName(name))
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

    public <T> T registerGauge(
            String name,
            String description,
            T stateObject,
            ToDoubleFunction<T> valueFunction,
            String... tags) {
        Gauge.builder(resolveMetricName(name), stateObject, valueFunction)
                .description(description)
                .tags(parseTags(tags))
                .register(meterRegistry);
        return stateObject;
    }

    public AtomicInteger registerAtomicInteger(
            String name, String description, AtomicInteger atomicInteger, String... tags) {
        Gauge.builder(resolveMetricName(name), atomicInteger, val -> val != null ? val.get() : 0)
                .description(description)
                .tags(parseTags(tags))
                .register(meterRegistry);
        return atomicInteger;
    }

    public AtomicLong registerAtomicLong(
            String name, String description, AtomicLong atomicLong, String... tags) {
        Gauge.builder(resolveMetricName(name), atomicLong, val -> val != null ? val.get() : 0L)
                .description(description)
                .tags(parseTags(tags))
                .register(meterRegistry);
        return atomicLong;
    }

    // SLO / SLI & Saturation Tracking

    /** Record database query duration, status, and table for SRE latency percentiles. */
    public void recordDatabaseQuery(
            String database, String table, Duration duration, boolean success) {
        String status = success ? "SUCCESS" : "FAILURE";
        recordTime(
                "database.query.duration",
                duration,
                "database",
                database != null ? database : "default",
                "table",
                table != null ? table : "unknown",
                "status",
                status);
        incrementCounter(
                "database.query.total",
                "database",
                database != null ? database : "default",
                "table",
                table != null ? table : "unknown",
                "status",
                status);
    }

    /** Track connection pool saturation (active, idle, max). */
    public void recordConnectionPoolStats(String poolName, int active, int idle, int max) {
        String base = "pool." + (poolName != null ? poolName : "default");
        Gauge.builder(resolveMetricName(base + ".active"), () -> active).register(meterRegistry);
        Gauge.builder(resolveMetricName(base + ".idle"), () -> idle).register(meterRegistry);
        Gauge.builder(resolveMetricName(base + ".max"), () -> max).register(meterRegistry);
        if (max > 0) {
            double saturation = (double) active / max * 100.0;
            Gauge.builder(resolveMetricName(base + ".saturation.percent"), () -> saturation)
                    .register(meterRegistry);
        }
    }

    /** Record circuit breaker state transitions (0 = CLOSED, 1 = HALF_OPEN, 2 = OPEN). */
    public void recordCircuitBreakerState(String name, String state) {
        int stateCode =
                "OPEN".equalsIgnoreCase(state) ? 2 : ("HALF_OPEN".equalsIgnoreCase(state) ? 1 : 0);
        circuitBreakerStateGauges
                .computeIfAbsent(
                        name,
                        n -> {
                            AtomicInteger gaugeVal = new AtomicInteger(stateCode);
                            Gauge.builder(
                                            resolveMetricName("resilience.circuitbreaker.state"),
                                            gaugeVal,
                                            val -> val != null ? val.get() : 0)
                                    .tag("name", n)
                                    .description(
                                            "Circuit Breaker State: 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
                                    .register(meterRegistry);
                            return gaugeVal;
                        })
                .set(stateCode);
    }

    /** Record circuit breaker call outcome (success, failure, short_circuited, fallback). */
    public void recordCircuitBreakerCall(String name, String outcome) {
        incrementCounter(
                "resilience.circuitbreaker.calls",
                "name",
                name != null ? name : "default",
                "outcome",
                outcome != null ? outcome : "unknown");
    }

    /** Record retry attempts and outcome. */
    public void recordRetry(String operation, int attempt, boolean success) {
        incrementCounter(
                "resilience.retry.total",
                "operation",
                operation != null ? operation : "unknown",
                "attempt",
                String.valueOf(attempt),
                "success",
                String.valueOf(success));
    }

    /** Record resilience fallback executions. */
    public void recordFallback(String operation, String reason) {
        incrementCounter(
                "resilience.fallback.total",
                "operation",
                operation != null ? operation : "unknown",
                "reason",
                reason != null ? reason : "unknown");
    }

    /**
     * Record Error Budget consumption and remaining budget percentage for SLA. E.g. targetSla =
     * 99.9 (0.1% allowed error rate), currentAvailability = 99.85 -> remaining budget = 50%
     */
    public void recordErrorBudget(String service, double targetSla, double currentAvailability) {
        double maxErrorRate = 100.0 - targetSla;
        double currentErrorRate = Math.max(0.0, 100.0 - currentAvailability);
        double remainingPercent =
                (maxErrorRate > 0)
                        ? Math.max(0.0, (maxErrorRate - currentErrorRate) / maxErrorRate * 100.0)
                        : 100.0;

        errorBudgetGauges
                .computeIfAbsent(
                        service,
                        s -> {
                            AtomicReference<Double> ref = new AtomicReference<>(remainingPercent);
                            Gauge.builder(
                                            resolveMetricName("slo.error_budget.remaining_percent"),
                                            ref,
                                            r -> (r != null && r.get() != null) ? r.get() : 0.0)
                                    .tag("service", s)
                                    .description(
                                            "Remaining Error Budget percentage based on SLA target")
                                    .register(meterRegistry);
                            return ref;
                        })
                .set(remainingPercent);
    }

    /** Monitor ThreadPool saturation (active, queued, max pool size). */
    public void registerThreadPoolMetrics(String poolName, ThreadPoolExecutor executor) {
        if (executor == null) {
            return;
        }
        String prefix = "threadpool." + (poolName != null ? poolName : "custom");
        registerGauge(
                prefix + ".active.threads",
                "Active executing worker threads",
                executor,
                e -> e != null ? e.getActiveCount() : 0);
        registerGauge(
                prefix + ".pool.size",
                "Current thread pool size",
                executor,
                e -> e != null ? e.getPoolSize() : 0);
        registerGauge(
                prefix + ".queue.size",
                "Queued tasks awaiting worker thread",
                executor,
                e -> (e != null && e.getQueue() != null) ? e.getQueue().size() : 0);
        registerGauge(
                prefix + ".completed.tasks",
                "Total completed tasks",
                executor,
                e -> e != null ? e.getCompletedTaskCount() : 0);
    }

    // Business Metrics (ShareRide Domain)

    /** Record ride booking requested. */
    public void recordRideBookingRequested(String city, String vehicleType) {
        incrementCounter(
                "ride.booking.requested",
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "vehicle_type",
                vehicleType != null ? vehicleType.toLowerCase() : "standard");
    }

    /**
     * Record ride booking outcome (SUCCESS, CANCELLED_BY_PASSENGER, CANCELLED_BY_DRIVER,
     * NO_DRIVER_FOUND, FAILED).
     */
    public void recordRideBookingOutcome(
            String status, String city, String vehicleType, String reason) {
        incrementCounter(
                "ride.booking.outcome",
                "status",
                status != null ? status : "UNKNOWN",
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "vehicle_type",
                vehicleType != null ? vehicleType.toLowerCase() : "standard",
                "reason",
                reason != null ? reason : "none");
    }

    /** Record completed trip metrics (duration and distance in km). */
    public void recordRideTripCompleted(
            String city, String vehicleType, Duration duration, double distanceKm) {
        recordTime(
                "ride.trip.duration",
                duration,
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "vehicle_type",
                vehicleType != null ? vehicleType.toLowerCase() : "standard");
        recordSummary(
                "ride.trip.distance.km",
                distanceKm,
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "vehicle_type",
                vehicleType != null ? vehicleType.toLowerCase() : "standard");
        incrementCounter(
                "ride.trip.completed",
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "vehicle_type",
                vehicleType != null ? vehicleType.toLowerCase() : "standard");
    }

    /** Set active drivers count by city and vehicle type. */
    public void setActiveDrivers(String city, String vehicleType, int count) {
        String key =
                (city != null ? city.toLowerCase() : "unknown")
                        + ":"
                        + (vehicleType != null ? vehicleType.toLowerCase() : "standard");
        activeDriversGauges
                .computeIfAbsent(
                        key,
                        k -> {
                            AtomicInteger gaugeVal = new AtomicInteger(count);
                            Gauge.builder(
                                            resolveMetricName("driver.active.count"),
                                            gaugeVal,
                                            val -> val != null ? val.get() : 0)
                                    .tag("city", city != null ? city.toLowerCase() : "unknown")
                                    .tag(
                                            "vehicle_type",
                                            vehicleType != null
                                                    ? vehicleType.toLowerCase()
                                                    : "standard")
                                    .description("Currently active and online drivers")
                                    .register(meterRegistry);
                            return gaugeVal;
                        })
                .set(count);
    }

    /** Record passenger pickup wait time distribution. */
    public void recordPassengerWaitTime(Duration waitTime, String city, String vehicleType) {
        recordTime(
                "ride.passenger.wait_time",
                waitTime,
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "vehicle_type",
                vehicleType != null ? vehicleType.toLowerCase() : "standard");
    }

    /** Record dynamic pricing engine calculation duration and surge multiplier tier. */
    public void recordPricingCalculationTime(
            Duration calculationTime, String city, String surgeTier) {
        recordTime(
                "pricing.calculation.duration",
                calculationTime,
                "city",
                city != null ? city.toLowerCase() : "unknown",
                "surge_tier",
                surgeTier != null ? surgeTier : "1.0x");
    }

    /** Record payment transaction outcome, gateway, method, and amount. */
    public void recordPaymentTransaction(
            String gateway, String paymentMethod, String status, double amount, String currency) {
        incrementCounter(
                "payment.transaction.total",
                "gateway",
                gateway != null ? gateway.toLowerCase() : "unknown",
                "method",
                paymentMethod != null ? paymentMethod.toLowerCase() : "unknown",
                "status",
                status != null ? status : "UNKNOWN",
                "currency",
                currency != null ? currency.toUpperCase() : "VND");
        recordSummary(
                "payment.transaction.amount",
                amount,
                "gateway",
                gateway != null ? gateway.toLowerCase() : "unknown",
                "status",
                status != null ? status : "UNKNOWN",
                "currency",
                currency != null ? currency.toUpperCase() : "VND");
    }

    /**
     * Track user signup funnel conversion steps (e.g. INITIATED, PHONE_VERIFIED, PROFILE_COMPLETED,
     * DOCUMENT_UPLOADED, APPROVED).
     */
    public void recordUserSignupStep(String step, String status, String channel, String userType) {
        incrementCounter(
                "user.signup.funnel",
                "step",
                step != null ? step.toUpperCase() : "UNKNOWN",
                "status",
                status != null ? status : "SUCCESS",
                "channel",
                channel != null ? channel.toLowerCase() : "organic",
                "user_type",
                userType != null ? userType.toLowerCase() : "passenger");
    }

    // Security Observability Events

    /**
     * Record authentication failure (e.g. invalid credentials, expired token, signature mismatch).
     */
    public void recordAuthFailure(String authType, String reason, String clientIpPrefix) {
        incrementCounter(
                "security.auth.failure",
                "auth_type",
                authType != null ? authType : "jwt",
                "reason",
                reason != null ? reason : "invalid_credentials",
                "client_ip_prefix",
                sanitizeIpPrefix(clientIpPrefix));
    }

    /** Record successful authentication. */
    public void recordAuthSuccess(String authType, String role) {
        incrementCounter(
                "security.auth.success",
                "auth_type",
                authType != null ? authType : "jwt",
                "role",
                role != null ? role : "user");
    }

    /** Record authorization failure (HTTP 403 Forbidden). */
    public void recordAccessDenied(String resource, String requiredRole, String action) {
        incrementCounter(
                "security.access.denied",
                "resource",
                resource != null ? resource : "unknown",
                "required_role",
                requiredRole != null ? requiredRole : "unknown",
                "action",
                action != null ? action : "read");
    }

    /** Record rate limiting trigger event (HTTP 429 Too Many Requests). */
    public void recordRateLimitExceeded(String clientIpPrefix, String endpoint, String limitType) {
        incrementCounter(
                "security.ratelimit.exceeded",
                "endpoint",
                endpoint != null ? endpoint : "unknown",
                "limit_type",
                limitType != null ? limitType : "ip",
                "client_ip_prefix",
                sanitizeIpPrefix(clientIpPrefix));
    }

    /**
     * Record suspicious activity detection (SQL injection attempt, credential stuffing, brute
     * force).
     */
    public void recordSuspiciousActivity(
            String activityType, String clientIpPrefix, String severity) {
        incrementCounter(
                "security.suspicious.activity",
                "activity_type",
                activityType != null ? activityType : "unknown",
                "severity",
                severity != null ? severity.toUpperCase() : "MEDIUM",
                "client_ip_prefix",
                sanitizeIpPrefix(clientIpPrefix));
    }

    /** Track API Key usage and validity. */
    public void recordApiKeyUsage(
            String apiKeyId, String clientName, String endpoint, boolean valid) {
        incrementCounter(
                "security.apikey.usage",
                "api_key_id",
                apiKeyId != null ? apiKeyId : "unknown",
                "client_name",
                clientName != null ? clientName : "unknown",
                "endpoint",
                endpoint != null ? endpoint : "unknown",
                "valid",
                String.valueOf(valid));
    }

    // Distributed Lock Observability

    /** Record distributed lock acquisition duration and outcome. */
    public void recordLockAcquire(
            String lockKey, String lockType, String status, Duration duration) {
        String sanitizedKey = sanitizeLockKey(lockKey);
        String type =
                (lockType != null && !lockType.isBlank()) ? lockType.toUpperCase() : "STANDARD";
        String stat = (status != null && !status.isBlank()) ? status.toUpperCase() : "UNKNOWN";

        incrementCounter("lock.acquire.total", "name", sanitizedKey, "type", type, "status", stat);

        if (duration != null) {
            recordTime(
                    "lock.acquire.duration",
                    duration,
                    "name",
                    sanitizedKey,
                    "type",
                    type,
                    "status",
                    stat);
        }
    }

    /** Record duration for which the lock was held by a thread/task. */
    public void recordLockHold(String lockKey, String lockType, Duration duration) {
        if (duration != null) {
            String sanitizedKey = sanitizeLockKey(lockKey);
            String type =
                    (lockType != null && !lockType.isBlank()) ? lockType.toUpperCase() : "STANDARD";
            recordTime("lock.hold.duration", duration, "name", sanitizedKey, "type", type);
        }
    }

    /** Record lock release outcome (SUCCESS, MISMATCH, ERROR). */
    public void recordLockRelease(String lockKey, String lockType, String status) {
        String sanitizedKey = sanitizeLockKey(lockKey);
        String type =
                (lockType != null && !lockType.isBlank()) ? lockType.toUpperCase() : "STANDARD";
        String stat = (status != null && !status.isBlank()) ? status.toUpperCase() : "UNKNOWN";

        incrementCounter("lock.release.total", "name", sanitizedKey, "type", type, "status", stat);
    }

    /** Record lock renewal (TTL refresh) outcome. */
    public void recordLockRenewal(String lockKey, boolean success) {
        String sanitizedKey = sanitizeLockKey(lockKey);
        incrementCounter(
                "lock.renew.total",
                "name",
                sanitizedKey,
                "status",
                success ? "SUCCESS" : "FAILURE");
    }

    /** Record lock acquire retry attempt. */
    public void recordLockRetry(String lockKey, String lockType, int attempt) {
        String sanitizedKey = sanitizeLockKey(lockKey);
        String type =
                (lockType != null && !lockType.isBlank()) ? lockType.toUpperCase() : "STANDARD";
        incrementCounter(
                "lock.retries.total",
                "name",
                sanitizedKey,
                "type",
                type,
                "attempt",
                String.valueOf(attempt));
    }

    private String sanitizeLockKey(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            return "unknown";
        }
        String key = lockKey;
        if (key.startsWith("lock:fair:queue:")) {
            key = key.substring("lock:fair:queue:".length());
        } else if (key.startsWith("lock:fair:")) {
            key = key.substring("lock:fair:".length());
        } else if (key.startsWith("lock:")) {
            key = key.substring("lock:".length());
        }
        String[] parts = key.split(":");
        if (parts.length > 2) {
            return parts[0] + ":" + parts[1];
        }
        return key;
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
