package com.shareride.common.probe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationListener;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kubernetes Startup Probe indicator (/actuator/health/startup).
 * Holds the startup probe in DOWN/STARTING status during warm-up, database schema migration,
 * and connection pool priming, then transitions to UP upon {@link ApplicationReadyEvent}
 * or manual invocation of {@link #markComplete()}.
 * Protects slow-starting pods from premature liveness probe kills.
 */
public class StartupProbeHealthIndicator implements HealthIndicator, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupProbeHealthIndicator.class);

    public enum StartupState {
        INITIALIZING,
        READY,
        FAILED
    }

    private final Instant startTime = Instant.now();
    private final long maxStartupTimeoutSeconds;
    private final AtomicReference<StartupState> state = new AtomicReference<>(StartupState.INITIALIZING);
    private final AtomicReference<String> failureReason = new AtomicReference<>(null);
    private final AtomicBoolean readyEventFired = new AtomicBoolean(false);
    private volatile Long readyDurationMs = null;

    public StartupProbeHealthIndicator() {
        this(120L); // 120 seconds default startup window
    }

    public StartupProbeHealthIndicator(long maxStartupTimeoutSeconds) {
        this.maxStartupTimeoutSeconds = maxStartupTimeoutSeconds;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        readyEventFired.set(true);
        markComplete();
    }

    public boolean isComplete() {
        return state.get() == StartupState.READY;
    }

    public void markComplete() {
        if (state.compareAndSet(StartupState.INITIALIZING, StartupState.READY)) {
            readyDurationMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("Kubernetes startup probe marked READY in {}ms", readyDurationMs);
        }
    }

    public void markFailed(String reason) {
        failureReason.set(reason);
        state.set(StartupState.FAILED);
        log.error("Kubernetes startup probe marked FAILED. Reason: {}", reason);
    }

    @Override
    public Health health() {
        StartupState currentState = state.get();
        Duration elapsed = Duration.between(startTime, Instant.now());

        // Check if exceeded max startup window
        if (currentState == StartupState.INITIALIZING && elapsed.toMillis() > maxStartupTimeoutSeconds * 1000L) {
            markFailed("Startup timeout exceeded " + maxStartupTimeoutSeconds + "s");
            currentState = StartupState.FAILED;
        }

        Health.Builder builder = (currentState == StartupState.READY) ? Health.up() : Health.down();

        builder.withDetail("state", currentState.name())
               .withDetail("elapsedSeconds", elapsed.getSeconds())
               .withDetail("maxTimeoutSeconds", maxStartupTimeoutSeconds);

        if (readyDurationMs != null) {
            builder.withDetail("startupDurationMs", readyDurationMs);
        }

        if (failureReason.get() != null) {
            builder.withDetail("failureReason", failureReason.get());
        }

        return builder.build();
    }
}
