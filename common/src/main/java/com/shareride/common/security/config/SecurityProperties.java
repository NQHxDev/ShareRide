package com.shareride.common.security.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for security settings.
 *
 * <p>Allows microservices to declare service-specific public endpoints in {@code application.yml}:
 *
 * <pre>
 * shareride:
 *   security:
 *     public-endpoints:
 *       - /auth/v1/login
 *       - /auth/v1/register
 *       - /auth/v1/refresh
 *       - /auth/v1/public/**
 * </pre>
 */
@ConfigurationProperties(prefix = "shareride.security")
public class SecurityProperties {

    private List<String> publicEndpoints = new ArrayList<>();

    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }

    public void setPublicEndpoints(List<String> publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }
}
