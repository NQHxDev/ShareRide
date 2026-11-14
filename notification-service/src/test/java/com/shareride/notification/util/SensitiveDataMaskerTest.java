package com.shareride.notification.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void shouldMaskSensitiveParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "Nguyễn Văn A");
        params.put("verificationCode", "123456");
        params.put("otpCode", "654321");
        params.put("resetToken", "abc-def-ghi");
        params.put("password", "MySecretPassword123");
        params.put("expiresInMinutes", 15);

        Map<String, Object> masked = SensitiveDataMasker.maskSensitiveData(params);

        assertThat(masked.get("userName")).isEqualTo("Nguyễn Văn A");
        assertThat(masked.get("verificationCode")).isEqualTo("***");
        assertThat(masked.get("otpCode")).isEqualTo("***");
        assertThat(masked.get("resetToken")).isEqualTo("***");
        assertThat(masked.get("password")).isEqualTo("***");
        assertThat(masked.get("expiresInMinutes")).isEqualTo(15);
    }

    @Test
    void shouldHandleNullAndEmptyMaps() {
        assertThat(SensitiveDataMasker.maskSensitiveData(null)).isEmpty();
        assertThat(SensitiveDataMasker.maskSensitiveData(new HashMap<>())).isEmpty();
    }

    @Test
    void shouldHandleMapWithNoSensitiveKeys() {
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "Test User");
        params.put("expiresInMinutes", 30);

        Map<String, Object> masked = SensitiveDataMasker.maskSensitiveData(params);

        assertThat(masked.get("userName")).isEqualTo("Test User");
        assertThat(masked.get("expiresInMinutes")).isEqualTo(30);
    }

    @Test
    void shouldMaskTokenInResetLinkQueryParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "Test User");
        params.put(
                "resetLink",
                "https://shareride.vn/reset-password?token=secret-reset-token-12345&userId=99");

        Map<String, Object> masked = SensitiveDataMasker.maskSensitiveData(params);

        assertThat(masked.get("userName")).isEqualTo("Test User");
        String maskedLink = (String) masked.get("resetLink");
        assertThat(maskedLink).isNotNull();
        assertThat(maskedLink).contains("token=***");
        assertThat(maskedLink).doesNotContain("secret-reset-token-12345");
        assertThat(maskedLink).contains("userId=99"); // Non-sensitive param preserved
    }

    @Test
    void shouldHandleResetLinkWithoutTokenParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("resetLink", "https://shareride.vn/reset-password?userId=99");

        Map<String, Object> masked = SensitiveDataMasker.maskSensitiveData(params);

        assertThat(masked.get("resetLink"))
                .isEqualTo("https://shareride.vn/reset-password?userId=99");
    }

    @Test
    void shouldHandleResetLinkWithMultipleQueryParams() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "resetLink",
                "https://shareride.vn/reset?token=abc123&lang=vi&userId=42&token=duplicate");

        Map<String, Object> masked = SensitiveDataMasker.maskSensitiveData(params);

        String maskedLink = (String) masked.get("resetLink");
        assertThat(maskedLink).contains("token=***");
        assertThat(maskedLink).contains("lang=vi");
        assertThat(maskedLink).contains("userId=42");
        assertThat(maskedLink).doesNotContain("abc123");
    }

    @Test
    void shouldMaskStandaloneTokenParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("token", "standalone-secret-token");
        params.put("userName", "User");

        Map<String, Object> masked = SensitiveDataMasker.maskSensitiveData(params);

        assertThat(masked.get("token")).isEqualTo("***");
        assertThat(masked.get("userName")).isEqualTo("User");
    }
}
