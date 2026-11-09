package com.shareride.common.versioning;

/** Enforcement modes for API Versioning across ShareRide microservices. */
public enum EnforcementMode {
    /**
     * Strict mode: Requests requesting an unsupported API version or omitting a required version
     * are rejected with HTTP 400 Bad Request.
     */
    STRICT,

    /**
     * Permissive mode: If a version is unsupported or omitted, the system falls back to default
     * version, issues an HTTP response warning header, and continues.
     */
    PERMISSIVE,

    /** Disabled mode: No versioning validation or enforcement is performed. */
    DISABLED
}
