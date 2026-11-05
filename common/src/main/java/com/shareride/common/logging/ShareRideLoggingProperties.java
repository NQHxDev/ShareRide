package com.shareride.common.logging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration properties for HTTP request logging, masking, sampling, and observability.
 */
@Data
@ConfigurationProperties(prefix = "shareride.logging")
public class ShareRideLoggingProperties {

    /**
     * Enable or disable the HTTP request logging filter.
     */
    private boolean enabled = true;

    /**
     * Threshold in milliseconds above which a request is logged at WARN level as a slow request.
     */
    private long slowThresholdMs = 1000;

    /**
     * Whether to log incoming HTTP headers (sensitive headers will be masked).
     */
    private boolean includeHeaders = false;

    /**
     * Sampling rate for normal INFO level HTTP logs (0.0 to 1.0, 1.0 = 100%).
     */
    private double infoSampleRate = 1.0;

    /**
     * Sampling rate for DEBUG level HTTP logs (0.0 to 1.0, 0.01 = 1%).
     */
    private double debugSampleRate = 0.01;

    /**
     * Whether to sanitize sensitive query parameters in URL logs.
     */
    private boolean maskUrlParameters = true;

    /**
     * Ant path patterns to exclude from access logging (e.g. actuator endpoints).
     */
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/**",
            "/favicon.ico",
            "/error"
    ));

    /**
     * Set of header names to mask when logging headers.
     */
    private Set<String> sensitiveHeaders = new HashSet<>(Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "proxy-authorization"
    ));
}
