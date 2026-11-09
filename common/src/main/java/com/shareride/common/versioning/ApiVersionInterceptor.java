package com.shareride.common.versioning;

import com.shareride.common.response.ResponseMetadataContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC Interceptor enforcing method/controller-level {@link ApiVersion} and {@link
 * DeprecatedApiVersion} annotations.
 */
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionInterceptor.class);

    private final ShareRideVersioningProperties properties;

    public ApiVersionInterceptor(ShareRideVersioningProperties properties) {
        this.properties = properties != null ? properties : new ShareRideVersioningProperties();
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (properties.getEnforcementMode() == EnforcementMode.DISABLED) {
            return true;
        }

        String activeVersion = ResponseMetadataContext.getApiVersion();
        String normalizedActive = ShareRideVersioningProperties.normalizeVersion(activeVersion);

        // 1. Check @ApiVersion
        ApiVersion apiVersion =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getMethod(), ApiVersion.class);
        if (apiVersion == null) {
            apiVersion =
                    AnnotatedElementUtils.findMergedAnnotation(
                            handlerMethod.getBeanType(), ApiVersion.class);
        }

        if (apiVersion != null) {
            List<String> allowedVersions = Arrays.asList(apiVersion.value());
            boolean matches =
                    allowedVersions.stream()
                            .map(ShareRideVersioningProperties::normalizeVersion)
                            .anyMatch(normalizedActive::equalsIgnoreCase);

            if (!matches) {
                if (properties.getEnforcementMode() == EnforcementMode.STRICT) {
                    throw new UnsupportedApiVersionException(
                            "Endpoint '"
                                    + request.getRequestURI()
                                    + "' does not support API version '"
                                    + activeVersion
                                    + "'. Supported versions: "
                                    + allowedVersions,
                            activeVersion,
                            allowedVersions);
                } else if (properties.getEnforcementMode() == EnforcementMode.PERMISSIVE) {
                    log.warn(
                            "Endpoint '{}' does not support requested version '{}'. Allowed: {}",
                            request.getRequestURI(),
                            activeVersion,
                            allowedVersions);
                    response.setHeader(
                            "X-API-Warn",
                            "Endpoint does not support version '" + activeVersion + "'");
                }
            }

            if (apiVersion.deprecated()) {
                applyDeprecation(
                        response, activeVersion, apiVersion.sunset(), apiVersion.successor(), null);
            }
        }

        // 2. Check @DeprecatedApiVersion
        DeprecatedApiVersion deprecatedApiVersion =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getMethod(), DeprecatedApiVersion.class);
        if (deprecatedApiVersion == null) {
            deprecatedApiVersion =
                    AnnotatedElementUtils.findMergedAnnotation(
                            handlerMethod.getBeanType(), DeprecatedApiVersion.class);
        }

        if (deprecatedApiVersion != null) {
            applyDeprecation(
                    response,
                    activeVersion,
                    deprecatedApiVersion.sunset(),
                    deprecatedApiVersion.successor(),
                    deprecatedApiVersion.message());
        }

        return true;
    }

    private void applyDeprecation(
            HttpServletResponse response,
            String version,
            String sunset,
            String successor,
            String message) {
        ResponseMetadataContext.setDeprecated(true);
        if (sunset != null && !sunset.isBlank()) {
            ResponseMetadataContext.setSunsetDate(sunset);
        }

        if (properties.isAddDeprecationHeaders()) {
            response.setHeader("Deprecation", "true");
            if (sunset != null && !sunset.isBlank()) {
                response.setHeader("Sunset", sunset);
            }
            if (successor != null && !successor.isBlank()) {
                response.setHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
            }
            String warnMsg =
                    (message != null && !message.isBlank())
                            ? message
                            : "API version "
                                    + version
                                    + " is deprecated"
                                    + (sunset != null ? " and will sunset on " + sunset : "");
            response.setHeader("Warning", "299 - \"" + warnMsg + "\"");
        }
    }
}
