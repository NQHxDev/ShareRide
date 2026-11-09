package com.shareride.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ShareRide API Gateway Microservice.
 *
 * <p>Single entry point for the ShareRide ecosystem handling traffic routing, distributed rate
 * limiting, circuit breaking, security headers, CORS, and request correlation.
 */
@SpringBootApplication(
        excludeName = {
            "com.shareride.common.persistence.PersistenceAutoConfiguration",
            "com.shareride.common.outbox.OutboxAutoConfiguration",
            "com.shareride.common.communication.CommunicationAutoConfiguration",
            "com.shareride.common.probe.ProbeAutoConfiguration",
            "com.shareride.common.versioning.ApiVersioningAutoConfiguration",
            "com.shareride.common.resilience.config.ResilienceAutoConfiguration",
            "com.shareride.common.cache.ShareRideCacheAutoConfiguration"
        })
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
