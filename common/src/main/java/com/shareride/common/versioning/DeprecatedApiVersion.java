package com.shareride.common.versioning;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly marks an endpoint or controller as deprecated with migration metadata. Triggers
 * RFC-compliant {@code Deprecation}, {@code Sunset}, and {@code Warning} response headers.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeprecatedApiVersion {

    /** Effective sunset date (e.g. "2026-12-31" or RFC 1123 date string). */
    String sunset() default "";

    /** Suggested successor endpoint or successor version (e.g. "/api/v2/rides"). */
    String successor() default "";

    /** Human-readable migration warning message. */
    String message() default "";
}
