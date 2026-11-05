package com.shareride.common.telemetry;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for parsing and formatting W3C Baggage headers (RFC baggage specification)
 * to propagate business context (tenantId, userId, correlationId, priority, featureFlags).
 */
public final class BaggageUtils {

    public static final String BAGGAGE_HEADER = "baggage";

    public static final String KEY_TENANT_ID = "tenantId";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_CORRELATION_ID = "correlationId";
    public static final String KEY_PRIORITY = "priority";
    public static final String KEY_FEATURE_FLAGS = "featureFlags";

    private BaggageUtils() {
    }

    /**
     * Parses a W3C baggage header string into key-value pairs.
     * E.g. "tenantId=vn,userId=u123,priority=HIGH"
     */
    public static Map<String, String> parseBaggage(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = headerValue.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                String key = trimmed.substring(0, eqIdx).trim();
                String rawValue = trimmed.substring(eqIdx + 1).trim();
                // Strip optional properties after semicolon: key=value;prop=xyz
                int semiIdx = rawValue.indexOf(';');
                if (semiIdx >= 0) {
                    rawValue = rawValue.substring(0, semiIdx).trim();
                }
                try {
                    String decoded = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                    result.put(key, decoded);
                } catch (Exception ignored) {
                    result.put(key, rawValue);
                }
            }
        }
        return result;
    }

    /**
     * Formats a map of baggage items into a valid W3C baggage header value.
     */
    public static String formatBaggage(Map<String, String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : items.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=");
            try {
                sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
            } catch (Exception e) {
                sb.append(entry.getValue());
            }
            first = false;
        }
        return sb.toString();
    }
}
