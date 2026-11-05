package com.shareride.common.filter;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.dynamic.DynamicLogLevelContext;
import com.shareride.common.telemetry.BaggageUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filter that extracts user identity, tenant isolation, session tracking,
 * and business baggage from headers forwarded by API Gateway or upstream microservices.
 * Stores context in {@link UserContextHolder} and SLF4J MDC.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String SESSION_ID_HEADER = "X-Session-Id";
    public static final String PRIORITY_HEADER = "X-Priority";
    public static final String REQUEST_PRIORITY_HEADER = "X-Request-Priority";
    public static final String FEATURE_FLAGS_HEADER = "X-Feature-Flags";
    public static final String DEVICE_ID_HEADER = "X-Device-Id";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String REAL_IP_HEADER = "X-Real-IP";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Extract W3C baggage header if present
        String rawBaggage = request.getHeader(BaggageUtils.BAGGAGE_HEADER);
        Map<String, String> baggageMap = BaggageUtils.parseBaggage(rawBaggage);

        // 2. Extract specific headers with fallback to W3C baggage
        String userId = resolveHeaderOrBaggage(request, USER_ID_HEADER, baggageMap, BaggageUtils.KEY_USER_ID);
        String tenantId = resolveHeaderOrBaggage(request, TENANT_ID_HEADER, baggageMap, BaggageUtils.KEY_TENANT_ID);
        String correlationId = resolveHeaderOrBaggage(request, CORRELATION_ID_HEADER, baggageMap, BaggageUtils.KEY_CORRELATION_ID);
        String sessionId = resolveHeaderOrBaggage(request, SESSION_ID_HEADER, baggageMap, "session_id");

        String priority = request.getHeader(PRIORITY_HEADER);
        if (priority == null || priority.isBlank()) {
            priority = request.getHeader(REQUEST_PRIORITY_HEADER);
        }
        if (priority == null || priority.isBlank()) {
            priority = baggageMap.get(BaggageUtils.KEY_PRIORITY);
        }

        String rawFeatureFlags = resolveHeaderOrBaggage(request, FEATURE_FLAGS_HEADER, baggageMap, BaggageUtils.KEY_FEATURE_FLAGS);
        Set<String> featureFlags = parseCommaSeparated(rawFeatureFlags);

        String email = request.getHeader(USER_EMAIL_HEADER);
        String rolesHeader = request.getHeader(USER_ROLES_HEADER);
        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        String userAgent = request.getHeader("User-Agent");
        String clientIp = resolveClientIp(request);
        String requestId = TraceIdContext.getRequestId();

        Set<String> roles = parseCommaSeparated(rolesHeader);

        // If correlationId is still empty, fallback to active traceId
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = TraceIdContext.getTraceId();
        }

        // Check if current user is registered for dynamic debugging
        if (userId != null && DynamicLogLevelContext.isDebugUser(userId)) {
            DynamicLogLevelContext.setDebugOverride(true);
        }

        UserContext userContext = UserContext.builder()
                .userId(userId)
                .email(email)
                .tenantId(tenantId)
                .correlationId(correlationId)
                .requestId(requestId)
                .sessionId(sessionId)
                .priority(priority)
                .roles(roles)
                .featureFlags(featureFlags)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .traceId(TraceIdContext.getTraceId())
                .spanId(TraceIdContext.getSpanId())
                .build();

        UserContextHolder.setContext(userContext);

        // 3. Populate MDC for structured logging & correlation
        if (userId != null && !userId.isBlank()) {
            MDC.put(MdcConstants.USER_ID, userId);
        }
        if (tenantId != null && !tenantId.isBlank()) {
            MDC.put(MdcConstants.TENANT_ID, tenantId);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            MDC.put(MdcConstants.SESSION_ID, sessionId);
        }
        if (priority != null && !priority.isBlank()) {
            MDC.put(MdcConstants.PRIORITY, priority);
        }
        if (!featureFlags.isEmpty()) {
            MDC.put(MdcConstants.FEATURE_FLAGS, String.join(",", featureFlags));
        }
        if (clientIp != null && !clientIp.isBlank()) {
            MDC.put(MdcConstants.CLIENT_IP, clientIp);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcConstants.USER_ID);
            MDC.remove(MdcConstants.TENANT_ID);
            MDC.remove(MdcConstants.CORRELATION_ID);
            MDC.remove(MdcConstants.SESSION_ID);
            MDC.remove(MdcConstants.PRIORITY);
            MDC.remove(MdcConstants.FEATURE_FLAGS);
            MDC.remove(MdcConstants.CLIENT_IP);
            UserContextHolder.clear();
        }
    }

    private String resolveHeaderOrBaggage(HttpServletRequest request, String headerName,
                                          Map<String, String> baggageMap, String baggageKey) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return baggageMap.get(baggageKey);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader(FORWARDED_FOR_HEADER);
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader(REAL_IP_HEADER);
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private Set<String> parseCommaSeparated(String header) {
        if (header == null || header.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
