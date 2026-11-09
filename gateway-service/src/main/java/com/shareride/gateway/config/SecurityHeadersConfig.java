package com.shareride.gateway.config;

import com.shareride.gateway.constant.GatewayConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enterprise HTTP Security Headers Configuration for ShareRide API Gateway.
 *
 * <p>Enforces industry-standard OWASP Secure Headers Project recommendations, protecting against
 * MIME-sniffing, clickjacking, cross-site scripting (XSS), cross-domain data leakage, and insecure
 * transport across all incoming and outgoing gateway traffic.
 *
 * <p>Configurable via property prefix {@code shareride.gateway.security.headers}.
 */
@Getter
@Setter
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "shareride.gateway.security.headers")
public class SecurityHeadersConfig {

    /** Whether global security headers injection is enabled. Defaults to {@code true}. */
    private boolean enabled = true;

    /**
     * Whether to override security headers if already present in downstream response. Defaults to
     * {@code false} (preserves downstream explicit customizations).
     */
    private boolean overrideExisting = false;

    /** X-Content-Type-Options header value. Defaults to {@code nosniff}. */
    private String contentTypeOptions = GatewayConstants.DEFAULT_X_CONTENT_TYPE_OPTIONS;

    /** X-Frame-Options header value. Defaults to {@code DENY}. */
    private String frameOptions = GatewayConstants.DEFAULT_X_FRAME_OPTIONS;

    /** X-XSS-Protection header value. Defaults to {@code 0} (OWASP modern recommendation). */
    private String xssProtection = GatewayConstants.DEFAULT_X_XSS_PROTECTION;

    /**
     * Strict-Transport-Security (HSTS) header value. Defaults to {@code max-age=31536000;
     * includeSubDomains; preload}.
     */
    private String strictTransportSecurity = GatewayConstants.DEFAULT_STRICT_TRANSPORT_SECURITY;

    /**
     * Content-Security-Policy (CSP) header value. Defaults to {@code default-src 'self';
     * frame-ancestors 'none'; object-src 'none'; base-uri 'self'}.
     */
    private String contentSecurityPolicy = GatewayConstants.DEFAULT_CONTENT_SECURITY_POLICY;

    /** Referrer-Policy header value. Defaults to {@code strict-origin-when-cross-origin}. */
    private String referrerPolicy = GatewayConstants.DEFAULT_REFERRER_POLICY;

    /**
     * Permissions-Policy header value. Defaults to {@code geolocation=(), camera=(), microphone=(),
     * payment=(), usb=()}.
     */
    private String permissionsPolicy = GatewayConstants.DEFAULT_PERMISSIONS_POLICY;

    /** X-Permitted-Cross-Domain-Policies header value. Defaults to {@code none}. */
    private String permittedCrossDomainPolicies =
            GatewayConstants.DEFAULT_X_PERMITTED_CROSS_DOMAIN_POLICIES;

    /**
     * Optional Cache-Control header value. Defaults to {@code null} (delegated to downstream
     * routes).
     */
    private String cacheControl;

    /** Optional Pragma header value. Defaults to {@code null}. */
    private String pragma;

    /** Optional Expires header value. Defaults to {@code null}. */
    private String expires;
}
