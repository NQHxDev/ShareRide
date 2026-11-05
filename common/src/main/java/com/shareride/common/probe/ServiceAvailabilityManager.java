package com.shareride.common.probe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Service to inspect and control Kubernetes startup, liveness, and readiness probes.
 * Supports graceful startup, traffic shedding, maintenance, and teardown.
 */
public class ServiceAvailabilityManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceAvailabilityManager.class);

    private final ApplicationAvailability availability;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<StartupProbeHealthIndicator> startupIndicatorProvider;

    public ServiceAvailabilityManager(ApplicationAvailability availability,
                                      ApplicationEventPublisher eventPublisher) {
        this(availability, eventPublisher, null);
    }

    public ServiceAvailabilityManager(ApplicationAvailability availability,
                                      ApplicationEventPublisher eventPublisher,
                                      ObjectProvider<StartupProbeHealthIndicator> startupIndicatorProvider) {
        this.availability = availability;
        this.eventPublisher = eventPublisher;
        this.startupIndicatorProvider = startupIndicatorProvider;
    }

    public LivenessState getLivenessState() {
        return availability.getLivenessState();
    }

    public ReadinessState getReadinessState() {
        return availability.getReadinessState();
    }

    public boolean isLive() {
        return getLivenessState() == LivenessState.CORRECT;
    }

    public boolean isReady() {
        return getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
    }

    public boolean isStartupComplete() {
        if (startupIndicatorProvider != null) {
            StartupProbeHealthIndicator indicator = startupIndicatorProvider.getIfAvailable();
            if (indicator != null) {
                return indicator.isComplete();
            }
        }
        return isReady();
    }

    /**
     * Signal to Kubernetes startup probe that initial warmups and setups are completed.
     */
    public void markStartupComplete() {
        log.info("Transitioning startup probe state to COMPLETE");
        if (startupIndicatorProvider != null) {
            StartupProbeHealthIndicator indicator = startupIndicatorProvider.getIfAvailable();
            if (indicator != null) {
                indicator.markComplete();
            }
        }
    }

    /**
     * Signal to Kubernetes startup probe that initialization failed fatally.
     */
    public void markStartupFailed(String reason) {
        log.error("Transitioning startup probe state to FAILED. Reason: {}", reason);
        if (startupIndicatorProvider != null) {
            StartupProbeHealthIndicator indicator = startupIndicatorProvider.getIfAvailable();
            if (indicator != null) {
                indicator.markFailed(reason);
            }
        }
    }

    /**
     * Signal to Kubernetes readiness probe that the service is ready to accept traffic.
     */
    public void markReady() {
        log.info("Transitioning readiness probe state to ACCEPTING_TRAFFIC");
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
    }

    /**
     * Signal to Kubernetes readiness probe that the service is temporarily refusing traffic
     * (e.g. cache warm-up, rate limiting, dependency outage, graceful drain).
     */
    public void markNotReady(String reason) {
        log.warn("Transitioning readiness probe state to REFUSING_TRAFFIC. Reason: {}", reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
    }

    /**
     * Signal to Kubernetes liveness probe that the application container is broken and needs restart.
     */
    public void markBroken(String reason) {
        log.error("Transitioning liveness probe state to BROKEN. Pod restart required. Reason: {}", reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
    }

    /**
     * Signal to Kubernetes liveness probe that the application container is functioning correctly.
     */
    public void markLive() {
        log.info("Transitioning liveness probe state to CORRECT");
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.CORRECT);
    }
}
