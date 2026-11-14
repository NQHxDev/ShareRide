package com.shareride.notification.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public final class SensitiveDataMasker {

    private static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS =
            Set.of(
                    "otpCode",
                    "verificationCode",
                    "resetToken",
                    "token",
                    "password",
                    "accessToken",
                    "refreshToken",
                    "apiKey",
                    "secret");

    private static final Set<String> URL_FIELDS_TO_SANITIZE =
            Set.of("resetLink", "verificationLink", "link");

    private SensitiveDataMasker() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> maskSensitiveData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> masked = new HashMap<>(data);

        // Mask sensitive keys
        for (String sensitiveKey : SENSITIVE_KEYS) {
            if (masked.containsKey(sensitiveKey)) {
                masked.put(sensitiveKey, MASK);
            }
        }

        // Sanitize URL fields (mask query parameters that might contain tokens)
        for (String urlField : URL_FIELDS_TO_SANITIZE) {
            if (masked.containsKey(urlField) && masked.get(urlField) instanceof String) {
                String url = (String) masked.get(urlField);
                masked.put(urlField, sanitizeUrl(url));
            }
        }

        return masked;
    }

    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        try {
            URI uri = new URI(url);

            // If there's no query string, return the original URL
            if (uri.getQuery() == null || uri.getQuery().isEmpty()) {
                return url;
            }

            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());

            // Mask sensitive parameters
            for (String sensitiveKey : SENSITIVE_KEYS) {
                if (queryParams.containsKey(sensitiveKey)) {
                    queryParams.put(sensitiveKey, MASK);
                }
            }

            // Rebuild URL with masked parameters
            UriComponentsBuilder builder =
                    UriComponentsBuilder.newInstance()
                            .scheme(uri.getScheme())
                            .host(uri.getHost())
                            .port(uri.getPort())
                            .path(uri.getPath());

            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }

            return builder.build().toUriString();

        } catch (URISyntaxException e) {
            log.warn("Failed to parse URL for sanitization: {}", url, e);
            return MASK;
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        String[] pairs = query.split("&");

        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value =
                        idx < pair.length() - 1
                                ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                                : "";
                params.put(key, value);
            }
        }

        return params;
    }
}
