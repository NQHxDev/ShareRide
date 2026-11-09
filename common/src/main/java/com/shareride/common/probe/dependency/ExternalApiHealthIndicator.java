package com.shareride.common.probe.dependency;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * Health indicator for external third-party API dependencies (Payment Gateway, Maps, SMS),
 * evaluating each dependency through isolated timeouts and circuit breakers.
 */
public class ExternalApiHealthIndicator implements HealthIndicator {

    private final ExternalDependencyRegistry registry;

    public ExternalApiHealthIndicator(ExternalDependencyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        if (registry == null || registry.isEmpty()) {
            return Health.up()
                    .withDetail("externalApis", "NONE_REGISTERED")
                    .withDetail("message", "No external API dependencies registered")
                    .build();
        }

        boolean criticalFailure = false;
        boolean degraded = false;
        Map<String, Object> details = new LinkedHashMap<>();

        for (Map.Entry<String, ExternalDependencyRegistry.DependencyCheck> entry :
                registry.getDependencies().entrySet()) {
            String name = entry.getKey();
            ExternalDependencyRegistry.DependencyCheck check = entry.getValue();

            Health depHealth = check.executor().execute(check.checkSupplier());
            boolean isUp = Status.UP.equals(depHealth.getStatus());

            Map<String, Object> depDetails = new LinkedHashMap<>(depHealth.getDetails());
            depDetails.put("status", depHealth.getStatus().getCode());
            depDetails.put("critical", check.critical());
            details.put(name, depDetails);

            if (!isUp) {
                if (check.critical()) {
                    criticalFailure = true;
                } else {
                    degraded = true;
                }
            }
        }

        if (criticalFailure) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("summary", "Critical external dependencies unavailable")
                    .build();
        } else if (degraded) {
            return Health.status(new Status("DEGRADED", "Non-critical external dependency issue"))
                    .withDetails(details)
                    .build();
        } else {
            return Health.up().withDetails(details).build();
        }
    }
}
