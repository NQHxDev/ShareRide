package com.shareride.common.logging.masking;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise data sanitization and masking utilities for URLs, JSON bodies, query parameters, HTTP
 * headers, and stack traces to prevent PII leakage.
 */
public final class DataMaskingUtils {

    public static final String MASK = "******";

    public static final Set<String> DEFAULT_SENSITIVE_KEYS =
            Set.of(
                    "password",
                    "passwd",
                    "pwd",
                    "secret",
                    "token",
                    "access_token",
                    "refreshtoken",
                    "refresh_token",
                    "apikey",
                    "api_key",
                    "authorization",
                    "auth",
                    "credential",
                    "pin",
                    "cvv",
                    "cvc",
                    "creditcard",
                    "cardnumber",
                    "card_number",
                    "ssn",
                    "privatekey");

    // Regex for URL query parameters
    private static final Pattern URL_PARAM_PATTERN = Pattern.compile("([?&])([^=&#]+)=([^&#]*)");

    // Regex for JSON fields: "key"\s*:\s*("([^"]*)"|(\d+)|(true|false|null))
    private static final Pattern JSON_FIELD_PATTERN =
            Pattern.compile("(?i)\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|([^,}\\]\\s]+))");

    // Regex for JWT tokens (header.payload.signature)
    private static final Pattern JWT_PATTERN =
            Pattern.compile("\\beyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]+\\b");

    // Regex for Bearer tokens
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9._~+\\-/=]+");

    // Regex for Credit Card numbers (13 to 19 digits with optional hyphens/spaces)
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile(
                    "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|6(?:011|5[0-9][0-9])[0-9]{12}|3[47][0-9]{13})\\b");

    private DataMaskingUtils() {}

    /** Checks whether a key (e.g. parameter, header, JSON property) is considered sensitive. */
    public static boolean isSensitive(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (String sensitive : DEFAULT_SENSITIVE_KEYS) {
            String normSens = sensitive.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            if (normalized.equals(normSens) || normalized.contains(normSens)) {
                return true;
            }
        }
        return false;
    }

    /** Masks sensitive query parameters in a URL or query string. */
    public static String maskUrl(String url) {
        if (url == null || url.isBlank() || !url.contains("?")) {
            return url;
        }

        Matcher matcher = URL_PARAM_PATTERN.matcher(url);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String delimiter = matcher.group(1);
            String paramName = matcher.group(2);
            String paramVal = matcher.group(3);

            if (isSensitive(paramName)) {
                matcher.appendReplacement(
                        sb, Matcher.quoteReplacement(delimiter + paramName + "=" + MASK));
            } else {
                matcher.appendReplacement(
                        sb, Matcher.quoteReplacement(delimiter + paramName + "=" + paramVal));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Sanitizes a map of request parameters (such as form params or query params). */
    public static Map<String, Object> maskParams(Map<?, ?> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : params.entrySet()) {
            String key = entry.getKey() != null ? String.valueOf(entry.getKey()) : null;
            Object value = entry.getValue();

            if (isSensitive(key)) {
                masked.put(key, MASK);
            } else if (value instanceof Map<?, ?> nestedMap) {
                masked.put(key, maskParams(nestedMap));
            } else if (value instanceof String strVal) {
                masked.put(key, redactText(strVal));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    /** Masks sensitive fields in a JSON string. */
    public static String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        Matcher matcher = JSON_FIELD_PATTERN.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (isSensitive(key)) {
                matcher.appendReplacement(
                        sb, Matcher.quoteReplacement("\"" + key + "\":\"" + MASK + "\""));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Masks an HTTP header if the header name indicates sensitivity. */
    public static String maskHeader(String headerName, String headerValue) {
        if (headerValue == null) {
            return null;
        }
        if (headerName != null && isSensitive(headerName)) {
            return MASK;
        }
        return headerValue;
    }

    /** Redacts PII patterns (Bearer tokens, Credit Card numbers, JWT tokens) from free text. */
    public static String redactText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        result = BEARER_PATTERN.matcher(result).replaceAll("Bearer " + MASK);
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("****-****-****-****");
        result = JWT_PATTERN.matcher(result).replaceAll(MASK);
        return result;
    }

    /** Masks an email address (e.g. john@domain.com -> j***@domain.com). */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 0 || atIdx == email.length() - 1 || !email.substring(atIdx).contains(".")) {
            return MASK;
        }
        String user = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        return user.charAt(0) + "***" + domain;
    }

    /** Masks phone numbers, preserving only the last 4 digits. */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return MASK;
        }
        String last4 = digits.substring(digits.length() - 4);
        int maskCount = digits.length() - 4;
        return "*".repeat(maskCount) + last4;
    }

    /**
     * Redacts stack trace text from a Throwable to ensure no sensitive credentials or tokens leak.
     */
    public static String redactStackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return redactText(sw.toString());
    }
}
