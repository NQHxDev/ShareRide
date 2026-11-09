package com.shareride.common.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.LocaleContextHolderUtil;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.ResponseMetadataContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that enforces API versioning rules across incoming HTTP requests. Evaluates version
 * validity, attaches RFC-standard response headers, manages thread-bound context, and tracks
 * observability metrics.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ApiVersionEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionEnforcementFilter.class);
    public static final String API_VERSION_ATTRIBUTE = "shareride.apiVersion";

    private final ShareRideVersioningProperties properties;
    private final ApiVersionResolver resolver;
    private final ObjectProvider<TemplateMessageSource> messageSourceProvider;
    private final ObjectProvider<ShareRideMetrics> metricsProvider;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiVersionEnforcementFilter(
            ShareRideVersioningProperties properties,
            ApiVersionResolver resolver,
            ObjectProvider<TemplateMessageSource> messageSourceProvider,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.properties = properties != null ? properties : new ShareRideVersioningProperties();
        this.resolver =
                resolver != null ? resolver : new DefaultApiVersionResolver(this.properties);
        this.messageSourceProvider = messageSourceProvider;
        this.metricsProvider = metricsProvider;

        ObjectMapper mapper =
                (objectMapperProvider != null) ? objectMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
        }
        try {
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        } catch (Throwable ignored) {
        }
        this.objectMapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (properties.getExcludePaths() != null) {
            for (String exclude : properties.getExcludePaths()) {
                if (pathMatcher.match(exclude, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (properties.getEnforcementMode() == EnforcementMode.DISABLED) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> optionalVersion = resolver.resolveVersion(request);
        String activeVersion;

        if (optionalVersion.isEmpty()) {
            if (properties.isRequireVersion()
                    && properties.getEnforcementMode() == EnforcementMode.STRICT) {
                recordMetric("missing", "REQUIRED_MISSING");
                String message =
                        resolveMessage(
                                "API_VERSION_REQUIRED",
                                Map.of(),
                                "API version is required but was not provided");
                writeErrorResponse(
                        request, response, ResponseCode.UNSUPPORTED_API_VERSION.getCode(), message);
                return;
            }
            activeVersion =
                    ShareRideVersioningProperties.normalizeVersion(properties.getDefaultVersion());
        } else {
            String requestedVersion = optionalVersion.get();
            boolean supported = properties.isSupported(requestedVersion);

            if (!supported) {
                if (properties.getEnforcementMode() == EnforcementMode.STRICT) {
                    recordMetric(requestedVersion, "UNSUPPORTED");
                    Map<String, Object> params =
                            Map.of(
                                    "version",
                                    requestedVersion,
                                    "supported",
                                    String.join(", ", properties.getSupportedVersions()));
                    String message =
                            resolveMessage(
                                    "API_VERSION_NOT_SUPPORTED",
                                    params,
                                    "API version '"
                                            + requestedVersion
                                            + "' is not supported. Supported versions: "
                                            + properties.getSupportedVersions());
                    writeErrorResponse(
                            request,
                            response,
                            ResponseCode.UNSUPPORTED_API_VERSION.getCode(),
                            message);
                    return;
                } else if (properties.getEnforcementMode() == EnforcementMode.PERMISSIVE) {
                    recordMetric(requestedVersion, "PERMISSIVE_FALLBACK");
                    response.setHeader(
                            "X-API-Warn",
                            "Unsupported API version '"
                                    + requestedVersion
                                    + "', falling back to '"
                                    + properties.getDefaultVersion()
                                    + "'");
                    activeVersion =
                            ShareRideVersioningProperties.normalizeVersion(
                                    properties.getDefaultVersion());
                } else {
                    activeVersion = requestedVersion;
                }
            } else {
                activeVersion = requestedVersion;
                boolean deprecated = properties.isDeprecated(activeVersion);
                if (deprecated) {
                    recordMetric(activeVersion, "DEPRECATED");
                    ResponseMetadataContext.setDeprecated(true);
                    String sunset = properties.getSunsetDate(activeVersion);
                    if (sunset != null && !sunset.isBlank()) {
                        ResponseMetadataContext.setSunsetDate(sunset);
                    }
                    if (properties.isAddDeprecationHeaders()) {
                        response.setHeader("Deprecation", "true");
                        if (sunset != null && !sunset.isBlank()) {
                            response.setHeader("Sunset", sunset);
                        }
                        String warn =
                                "299 - \"API version "
                                        + activeVersion
                                        + " is deprecated"
                                        + (sunset != null ? " and will sunset on " + sunset : "")
                                        + "\"";
                        response.setHeader("Warning", warn);
                    }
                } else {
                    recordMetric(activeVersion, "SUPPORTED");
                }
            }
        }

        // Attach context & response headers
        ResponseMetadataContext.setApiVersion(activeVersion);
        request.setAttribute(API_VERSION_ATTRIBUTE, activeVersion);
        MDC.put(MdcConstants.API_VERSION, activeVersion);

        if (properties.isAddResponseHeaders()) {
            response.setHeader(properties.getHeaderName(), activeVersion);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcConstants.API_VERSION);
            ResponseMetadataContext.clear();
        }
    }

    private void recordMetric(String version, String status) {
        if (metricsProvider != null) {
            ShareRideMetrics metrics = metricsProvider.getIfAvailable();
            if (metrics != null) {
                metrics.recordApiVersionRequest(version, status);
            }
        }
    }

    private String resolveMessage(String key, Map<String, Object> params, String defaultMsg) {
        if (messageSourceProvider != null) {
            TemplateMessageSource ms = messageSourceProvider.getIfAvailable();
            if (ms != null) {
                Locale locale = LocaleContextHolderUtil.getLocale();
                String msg = ms.getMessage(key, params, locale);
                if (msg != null && !msg.equals(key)) {
                    return msg;
                }
            }
        }
        return defaultMsg;
    }

    private void writeErrorResponse(
            HttpServletRequest request, HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");

        String traceId = TraceIdContext.getTraceId();
        String errorId = UUID.randomUUID().toString();
        String path = request.getRequestURI();
        Instant now = Instant.now();

        String json = null;
        if (objectMapper != null) {
            try {
                BaseResponse<Void> errorBody =
                        BaseResponse.<Void>builder()
                                .success(false)
                                .code(code)
                                .message(message)
                                .errorId(errorId)
                                .traceId(traceId)
                                .timestamp(now)
                                .path(path)
                                .build();
                json = objectMapper.writeValueAsString(errorBody);
            } catch (Exception e) {
                log.debug("Falling back to manual JSON serialization: {}", e.getMessage());
            }
        }

        if (json == null) {
            json =
                    String.format(
                            "{\"success\":false,\"level\":\"ERROR\",\"code\":\"%s\","
                                    + "\"message\":\"%s\",\"errorId\":\"%s\",\"traceId\":%s,"
                                    + "\"timestamp\":\"%s\",\"path\":\"%s\"}",
                            escapeJson(code),
                            escapeJson(message),
                            escapeJson(errorId),
                            traceId != null ? "\"" + escapeJson(traceId) + "\"" : "null",
                            now.toString(),
                            escapeJson(path));
        }

        response.getWriter().write(json);
    }

    private String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
