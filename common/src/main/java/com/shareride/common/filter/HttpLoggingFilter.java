package com.shareride.common.filter;

import com.shareride.common.context.UserContextHolder;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.ShareRideLoggingProperties;
import com.shareride.common.logging.masking.DataMaskingUtils;
import com.shareride.common.logging.sampling.LogSampler;
import com.shareride.common.metrics.ShareRideMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that logs HTTP request lifecycle, measures duration, masks sensitive URLs/headers, applies
 * intelligent sampling, and alerts on slow requests.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private final ShareRideLoggingProperties properties;
    private final Optional<ShareRideMetrics> metrics;
    private final LogSampler sampler;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public HttpLoggingFilter(ShareRideLoggingProperties properties) {
        this(properties, (ObjectProvider<ShareRideMetrics>) null, null);
    }

    public HttpLoggingFilter(
            ShareRideLoggingProperties properties,
            ObjectProvider<ShareRideMetrics> metricsProvider) {
        this(properties, metricsProvider, null);
    }

    public HttpLoggingFilter(
            ShareRideLoggingProperties properties,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<LogSampler> samplerProvider) {
        this.properties = properties != null ? properties : new ShareRideLoggingProperties();
        this.metrics =
                Optional.ofNullable(
                        metricsProvider != null ? metricsProvider.getIfAvailable() : null);
        this.sampler =
                (samplerProvider != null && samplerProvider.getIfAvailable() != null)
                        ? samplerProvider.getIfAvailable()
                        : new LogSampler(
                                this.properties.getInfoSampleRate(),
                                this.properties.getDebugSampleRate());
    }

    public HttpLoggingFilter(
            ShareRideLoggingProperties properties, Optional<ShareRideMetrics> metrics) {
        this.properties = properties != null ? properties : new ShareRideLoggingProperties();
        this.metrics = metrics != null ? metrics : Optional.empty();
        this.sampler =
                new LogSampler(
                        this.properties.getInfoSampleRate(), this.properties.getDebugSampleRate());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        if (properties.getExcludePaths() != null) {
            for (String pattern : properties.getExcludePaths()) {
                if (pathMatcher.match(pattern, path)) {
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

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath =
                (queryString != null && !queryString.isBlank()) ? uri + "?" + queryString : uri;

        if (properties.isMaskUrlParameters()) {
            fullPath = DataMaskingUtils.maskUrl(fullPath);
        }

        MDC.put(MdcConstants.HTTP_METHOD, method);
        MDC.put(MdcConstants.HTTP_URI, uri);

        if (properties.isIncludeHeaders() && log.isDebugEnabled()) {
            logHeaders(request);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String clientIp = UserContextHolder.getClientIp();
            String userId = UserContextHolder.getCurrentUserId().orElse("anonymous");
            boolean isSlow = duration >= properties.getSlowThresholdMs();
            // boolean isError = status >= 400;

            if (isSlow) {
                log.warn(
                        "[SLOW REQUEST] {} {} status={} took {}ms (threshold: {}ms) clientIp={}"
                                + " userId={}",
                        method,
                        fullPath,
                        status,
                        duration,
                        properties.getSlowThresholdMs(),
                        clientIp,
                        userId);
                metrics.ifPresent(m -> m.recordSlowRequest(method, uri, status));
            } else if (status >= 500) {
                log.error(
                        "[HTTP] {} {} status={} duration={}ms clientIp={} userId={}",
                        method,
                        fullPath,
                        status,
                        duration,
                        clientIp,
                        userId);
            } else if (status >= 400) {
                log.warn(
                        "[HTTP] {} {} status={} duration={}ms clientIp={} userId={}",
                        method,
                        fullPath,
                        status,
                        duration,
                        clientIp,
                        userId);
            } else {
                // Apply log sampling for normal INFO logs
                String traceId = TraceIdContext.getTraceId();
                if (sampler.shouldSample("INFO", traceId, false)) {
                    log.info(
                            "[HTTP] {} {} status={} duration={}ms clientIp={} userId={}",
                            method,
                            fullPath,
                            status,
                            duration,
                            clientIp,
                            userId);
                }
            }

            MDC.remove(MdcConstants.HTTP_METHOD);
            MDC.remove(MdcConstants.HTTP_URI);
        }
    }

    private void logHeaders(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder("Headers: [");
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value;
                if ((properties.getSensitiveHeaders() != null
                                && properties.getSensitiveHeaders().contains(name.toLowerCase()))
                        || DataMaskingUtils.isSensitive(name)) {
                    value = DataMaskingUtils.MASK;
                } else {
                    value = request.getHeader(name);
                }
                sb.append(name).append("=").append(value).append(", ");
            }
        }
        sb.append("]");
        log.debug(sb.toString());
    }
}
