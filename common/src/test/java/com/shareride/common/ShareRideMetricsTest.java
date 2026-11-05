package com.shareride.common;

import com.shareride.common.metrics.ShareRideMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ShareRideMetricsTest {

    private MeterRegistry meterRegistry;
    private ShareRideMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ShareRideMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should increment counter with prefix and tags correctly")
    void testIncrementCounter() {
        metrics.incrementCounter("ride.booking.success", "tenant", "vn", "tier", "standard");
        metrics.incrementCounter("ride.booking.success", 2.0, "tenant", "vn", "tier", "standard");

        Counter counter = meterRegistry.find("shareride.ride.booking.success")
                .tag("tenant", "vn")
                .tag("tier", "standard")
                .counter();

        assertNotNull(counter);
        assertEquals(3.0, counter.count());
    }

    @Test
    @DisplayName("Should record exceptions and slow requests correctly")
    void testRecordExceptionsAndSlowRequests() {
        metrics.recordException("VALIDATION_FAILED", "MethodArgumentNotValidException");
        Counter errorCounter = meterRegistry.find("shareride.errors.total")
                .tag("code", "VALIDATION_FAILED")
                .tag("exception", "MethodArgumentNotValidException")
                .counter();
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());

        metrics.recordSlowRequest("POST", "/api/v1/rides", 200);
        Counter slowCounter = meterRegistry.find("shareride.http.slow.requests")
                .tag("method", "POST")
                .tag("uri", "/api/v1/rides")
                .tag("status", "200")
                .counter();
        assertNotNull(slowCounter);
        assertEquals(1.0, slowCounter.count());

        metrics.recordHttpRequest("GET", "/api/v1/users", 200, Duration.ofMillis(55));
        Timer httpTimer = meterRegistry.find("shareride.http.requests.duration")
                .tag("method", "GET")
                .tag("uri", "/api/v1/users")
                .tag("status", "200")
                .timer();
        assertNotNull(httpTimer);
        assertEquals(1, httpTimer.count());
    }

    @Test
    @DisplayName("Should record execution duration and time supplier/runnable")
    void testRecordTimers() {
        metrics.recordTime("payment.process.time", Duration.ofMillis(120), "gateway", "stripe");
        metrics.recordTime("payment.process.time", 80, TimeUnit.MILLISECONDS, "gateway", "stripe");

        Timer timer = meterRegistry.find("shareride.payment.process.time")
                .tag("gateway", "stripe")
                .timer();

        assertNotNull(timer);
        assertEquals(2, timer.count());
        assertEquals(200, timer.totalTime(TimeUnit.MILLISECONDS), 5.0);

        // Test Supplier
        String result = metrics.time("auth.token.generate", () -> "jwt-token-123", "role", "driver");
        assertEquals("jwt-token-123", result);

        // Test Timer Sample
        Timer.Sample sample = metrics.startTimer();
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        metrics.stopTimer(sample, "async.job.duration", "type", "invoice");

        Timer asyncTimer = meterRegistry.find("shareride.async.job.duration")
                .tag("type", "invoice")
                .timer();
        assertNotNull(asyncTimer);
        assertEquals(1, asyncTimer.count());
    }

    @Test
    @DisplayName("Should register gauges and track dynamic state changes")
    void testGauges() {
        AtomicInteger activeDrivers = new AtomicInteger(5);
        metrics.registerAtomicInteger("driver.active.count", "Active drivers on road", activeDrivers, "city", "hanoi");

        Gauge gauge = meterRegistry.find("shareride.driver.active.count")
                .tag("city", "hanoi")
                .gauge();

        assertNotNull(gauge);
        assertEquals(5.0, gauge.value());

        activeDrivers.set(12);
        assertEquals(12.0, gauge.value());

        AtomicLong queueSize = new AtomicLong(100L);
        metrics.registerAtomicLong("booking.queue.size", "Queue depth", queueSize);
        Gauge queueGauge = meterRegistry.find("shareride.booking.queue.size").gauge();
        assertNotNull(queueGauge);
        assertEquals(100.0, queueGauge.value());
    }

    @Test
    @DisplayName("Should record histogram and distribution summaries")
    void testRecordSummary() {
        metrics.recordSummary("ride.trip.distance.km", 5.4, "city", "hcm");
        metrics.recordSummary("ride.trip.distance.km", 12.8, "city", "hcm");

        DistributionSummary summary = meterRegistry.find("shareride.ride.trip.distance.km")
                .tag("city", "hcm")
                .summary();

        assertNotNull(summary);
        assertEquals(2, summary.count());
        assertEquals(18.2, summary.totalAmount(), 0.01);
    }

    @Test
    @DisplayName("Should record business domain metrics for ride lifecycle")
    void testBusinessDomainMetrics() {
        metrics.recordRideBookingRequested("hcm", "suv");
        Counter reqCounter = meterRegistry.find("shareride.ride.booking.requested")
                .tag("city", "hcm")
                .tag("vehicle_type", "suv")
                .counter();
        assertNotNull(reqCounter);
        assertEquals(1.0, reqCounter.count());

        metrics.recordRideBookingOutcome("SUCCESS", "hcm", "suv", "none");
        Counter outcomeCounter = meterRegistry.find("shareride.ride.booking.outcome")
                .tag("status", "SUCCESS")
                .tag("city", "hcm")
                .counter();
        assertNotNull(outcomeCounter);
        assertEquals(1.0, outcomeCounter.count());

        metrics.recordRideTripCompleted("hcm", "suv", Duration.ofMinutes(15), 8.5);
        Timer tripTimer = meterRegistry.find("shareride.ride.trip.duration")
                .tag("city", "hcm")
                .timer();
        assertNotNull(tripTimer);
        assertEquals(1, tripTimer.count());

        metrics.setActiveDrivers("danang", "bike", 42);
        Gauge driverGauge = meterRegistry.find("shareride.driver.active.count")
                .tag("city", "danang")
                .tag("vehicle_type", "bike")
                .gauge();
        assertNotNull(driverGauge);
        assertEquals(42.0, driverGauge.value());

        metrics.recordPassengerWaitTime(Duration.ofMinutes(3), "hanoi", "car");
        Timer waitTimer = meterRegistry.find("shareride.ride.passenger.wait_time")
                .tag("city", "hanoi")
                .timer();
        assertNotNull(waitTimer);
        assertEquals(1, waitTimer.count());

        metrics.recordPricingCalculationTime(Duration.ofMillis(35), "hcm", "1.5x");
        Timer pricingTimer = meterRegistry.find("shareride.pricing.calculation.duration")
                .tag("city", "hcm")
                .tag("surge_tier", "1.5x")
                .timer();
        assertNotNull(pricingTimer);
        assertEquals(1, pricingTimer.count());

        metrics.recordPaymentTransaction("vnpay", "credit_card", "SUCCESS", 150000.0, "VND");
        Counter paymentCounter = meterRegistry.find("shareride.payment.transaction.total")
                .tag("gateway", "vnpay")
                .tag("status", "SUCCESS")
                .counter();
        assertNotNull(paymentCounter);
        assertEquals(1.0, paymentCounter.count());

        metrics.recordUserSignupStep("PHONE_VERIFIED", "SUCCESS", "referral", "passenger");
        Counter signupCounter = meterRegistry.find("shareride.user.signup.funnel")
                .tag("step", "PHONE_VERIFIED")
                .counter();
        assertNotNull(signupCounter);
        assertEquals(1.0, signupCounter.count());
    }

    @Test
    @DisplayName("Should record security events and rate limit metrics")
    void testSecurityObservabilityMetrics() {
        metrics.recordAuthFailure("JWT", "EXPIRED_TOKEN", "192.168.1.100");
        Counter authFailCounter = meterRegistry.find("shareride.security.auth.failure")
                .tag("auth_type", "JWT")
                .tag("reason", "EXPIRED_TOKEN")
                .counter();
        assertNotNull(authFailCounter);
        assertEquals(1.0, authFailCounter.count());

        metrics.recordAccessDenied("/api/v1/admin/rides", "ADMIN", "DELETE");
        Counter deniedCounter = meterRegistry.find("shareride.security.access.denied")
                .tag("action", "DELETE")
                .counter();
        assertNotNull(deniedCounter);
        assertEquals(1.0, deniedCounter.count());

        metrics.recordRateLimitExceeded("10.0.0.5", "/api/v1/auth/login", "ip");
        Counter rateLimitCounter = meterRegistry.find("shareride.security.ratelimit.exceeded")
                .tag("endpoint", "/api/v1/auth/login")
                .counter();
        assertNotNull(rateLimitCounter);
        assertEquals(1.0, rateLimitCounter.count());

        metrics.recordSuspiciousActivity("SQL_INJECTION", "172.16.0.1", "HIGH");
        Counter suspCounter = meterRegistry.find("shareride.security.suspicious.activity")
                .tag("severity", "HIGH")
                .counter();
        assertNotNull(suspCounter);
        assertEquals(1.0, suspCounter.count());

        metrics.recordApiKeyUsage("key_partner_99", "grab_partner", "/api/v1/fares", true);
        Counter apiKeyCounter = meterRegistry.find("shareride.security.apikey.usage")
                .tag("api_key_id", "key_partner_99")
                .counter();
        assertNotNull(apiKeyCounter);
        assertEquals(1.0, apiKeyCounter.count());
    }

    @Test
    @DisplayName("Should record SLO/SLI tracking, error budget, circuit breaker, and connection pool")
    void testSloAndResilienceMetrics() {
        metrics.recordDatabaseQuery("postgres", "bookings", Duration.ofMillis(12), true);
        Timer dbTimer = meterRegistry.find("shareride.database.query.duration")
                .tag("database", "postgres")
                .tag("table", "bookings")
                .timer();
        assertNotNull(dbTimer);
        assertEquals(1, dbTimer.count());

        metrics.recordConnectionPoolStats("hikari-main", 15, 5, 20);
        Gauge poolSat = meterRegistry.find("shareride.pool.hikari-main.saturation.percent").gauge();
        assertNotNull(poolSat);
        assertEquals(75.0, poolSat.value());

        metrics.recordCircuitBreakerState("payment-gateway", "OPEN");
        Gauge cbGauge = meterRegistry.find("shareride.resilience.circuitbreaker.state")
                .tag("name", "payment-gateway")
                .gauge();
        assertNotNull(cbGauge);
        assertEquals(2.0, cbGauge.value());

        metrics.recordCircuitBreakerCall("payment-gateway", "SHORT_CIRCUITED");
        Counter cbCallCounter = meterRegistry.find("shareride.resilience.circuitbreaker.calls")
                .tag("name", "payment-gateway")
                .tag("outcome", "SHORT_CIRCUITED")
                .counter();
        assertNotNull(cbCallCounter);
        assertEquals(1.0, cbCallCounter.count());

        metrics.recordRetry("external-sms", 2, true);
        Counter retryCounter = meterRegistry.find("shareride.resilience.retry.total")
                .tag("operation", "external-sms")
                .tag("attempt", "2")
                .counter();
        assertNotNull(retryCounter);
        assertEquals(1.0, retryCounter.count());

        metrics.recordFallback("driver-matching", "REDIS_DOWN");
        Counter fallbackCounter = meterRegistry.find("shareride.resilience.fallback.total")
                .tag("operation", "driver-matching")
                .counter();
        assertNotNull(fallbackCounter);
        assertEquals(1.0, fallbackCounter.count());

        metrics.recordErrorBudget("booking-service", 99.9, 99.95);
        Gauge budgetGauge = meterRegistry.find("shareride.slo.error_budget.remaining_percent")
                .tag("service", "booking-service")
                .gauge();
        assertNotNull(budgetGauge);
        assertEquals(50.0, budgetGauge.value(), 0.1);
    }
}
