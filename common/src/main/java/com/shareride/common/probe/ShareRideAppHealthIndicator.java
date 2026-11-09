package com.shareride.common.probe;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * HealthIndicator providing rich status details (liveness, readiness, uptime, application) under
 * Actuator `/actuator/health`.
 */
public class ShareRideAppHealthIndicator implements HealthIndicator {

    private final ServiceAvailabilityManager availabilityManager;
    private final String applicationName;
    private final Instant startupTime = Instant.now();

    public ShareRideAppHealthIndicator(
            ServiceAvailabilityManager availabilityManager,
            @Value("${spring.application.name:shareride-service}") String applicationName) {
        this.availabilityManager = availabilityManager;
        this.applicationName = applicationName;
    }

    @Override
    public Health health() {
        boolean live = availabilityManager.isLive();
        boolean ready = availabilityManager.isReady();

        Health.Builder builder = live ? Health.up() : Health.down();

        LivenessState livenessState = availabilityManager.getLivenessState();
        ReadinessState readinessState = availabilityManager.getReadinessState();

        String statusDetail;
        if (!live) {
            statusDetail = "DEADLOCK_OR_BROKEN";
        } else if (ready) {
            statusDetail = "OPERATIONAL";
        } else {
            statusDetail = "WARMING_UP_OR_DRAINING";
        }

        return builder.withDetail("application", applicationName)
                .withDetail("liveness", livenessState != null ? livenessState.name() : "UNKNOWN")
                .withDetail("readiness", readinessState != null ? readinessState.name() : "UNKNOWN")
                .withDetail(
                        "uptimeSeconds", Duration.between(startupTime, Instant.now()).getSeconds())
                .withDetail("state", statusDetail)
                .build();
    }
}
