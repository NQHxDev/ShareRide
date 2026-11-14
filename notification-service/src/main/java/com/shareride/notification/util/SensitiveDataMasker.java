package com.shareride.notification.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SensitiveDataMasker {

    private static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS =
            Set.of(
                    "otpCode",
                    "verificationCode",
                    "resetToken",
                    "password",
                    "accessToken",
                    "refreshToken",
                    "apiKey",
                    "secret");

    private SensitiveDataMasker() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> maskSensitiveData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> masked = new HashMap<>(data);
        for (String sensitiveKey : SENSITIVE_KEYS) {
            if (masked.containsKey(sensitiveKey)) {
                masked.put(sensitiveKey, MASK);
            }
        }
        return masked;
    }
}
