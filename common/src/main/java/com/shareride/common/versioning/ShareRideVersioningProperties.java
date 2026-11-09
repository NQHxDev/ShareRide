package com.shareride.common.versioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for API Versioning and Enforcement Strategy. */
@Data
@ConfigurationProperties(prefix = "shareride.versioning")
public class ShareRideVersioningProperties {

    /** Enable or disable API versioning framework. */
    private boolean enabled = true;

    /** Default fallback API version if client does not specify one. */
    private String defaultVersion = "v1";

    /** Supported API versions recognized across services. */
    private Set<String> supportedVersions = new HashSet<>(Arrays.asList("v1", "v2"));

    /** Deprecated API versions that remain functional but emit deprecation headers/warnings. */
    private Set<String> deprecatedVersions = new HashSet<>();

    /** Sunset dates for deprecated versions (version -> date string, e.g. "v1" -> "2026-12-31"). */
    private Map<String, String> sunsetDates = new HashMap<>();

    /** Enforcement mode (STRICT, PERMISSIVE, DISABLED). Default is STRICT. */
    private EnforcementMode enforcementMode = EnforcementMode.STRICT;

    /** HTTP request header name for API versioning. Default: "X-API-Version". */
    private String headerName = "X-API-Version";

    /** Query parameter name for API versioning. Default: "api-version". */
    private String paramName = "api-version";

    /** Regex pattern to extract API version from URI path. */
    private String uriPattern = "^/api/(v[0-9]+(?:\\.[0-9]+)?)(?:/.*)?$";

    /** Whether client MUST provide an API version explicitly (in STRICT mode). */
    private boolean requireVersion = false;

    /** Whether to include the resolved X-API-Version header in the HTTP response. */
    private boolean addResponseHeaders = true;

    /**
     * Whether to add RFC-compliant Deprecation and Sunset headers when deprecated version is used.
     */
    private boolean addDeprecationHeaders = true;

    /** URL path patterns excluded from version enforcement (e.g. actuator, swagger, health). */
    private List<String> excludePaths =
            new ArrayList<>(
                    Arrays.asList(
                            "/actuator/**",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/favicon.ico"));

    public boolean isSupported(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String norm = normalizeVersion(version);
        return supportedVersions.stream()
                .map(ShareRideVersioningProperties::normalizeVersion)
                .anyMatch(norm::equalsIgnoreCase);
    }

    public boolean isDeprecated(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String norm = normalizeVersion(version);
        return deprecatedVersions.stream()
                .map(ShareRideVersioningProperties::normalizeVersion)
                .anyMatch(norm::equalsIgnoreCase);
    }

    public String getSunsetDate(String version) {
        if (version == null || sunsetDates == null || sunsetDates.isEmpty()) {
            return null;
        }
        String norm = normalizeVersion(version);
        for (Map.Entry<String, String> entry : sunsetDates.entrySet()) {
            if (normalizeVersion(entry.getKey()).equalsIgnoreCase(norm)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Normalizes raw version string to lowercase with a leading 'v'. Examples: "1" -> "v1", "1.0"
     * -> "v1.0", "V2" -> "v2", "v1" -> "v1".
     */
    public static String normalizeVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String v = raw.trim().toLowerCase();
        if (v.startsWith("v")) {
            return v;
        }
        if (Character.isDigit(v.charAt(0))) {
            return "v" + v;
        }
        return v;
    }
}
