package com.shareride.common.logging.masking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataMaskingUtilsTest {

    @Test
    void testMaskUrl() {
        String url =
                "/api/v1/auth/login?token=secret123&client_id=web&password=myPass&redirect=/home";
        String masked = DataMaskingUtils.maskUrl(url);

        assertTrue(masked.contains("token=" + DataMaskingUtils.MASK));
        assertTrue(masked.contains("password=" + DataMaskingUtils.MASK));
        assertTrue(masked.contains("client_id=web"));
        assertTrue(masked.contains("redirect=/home"));

        assertNull(DataMaskingUtils.maskUrl(null));
        assertEquals("/api/v1/rides", DataMaskingUtils.maskUrl("/api/v1/rides"));
    }

    @Test
    void testMaskParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "john_doe");
        params.put("password", "p@ssword123");
        params.put("apiKey", "ak-999-xyz");
        params.put("count", 5);

        Map<String, Object> nested = new HashMap<>();
        nested.put("cvv", "123");
        nested.put("cardHolder", "John Doe");
        params.put("payment", nested);

        Map<String, Object> masked = DataMaskingUtils.maskParams(params);

        assertEquals("john_doe", masked.get("username"));
        assertEquals(DataMaskingUtils.MASK, masked.get("password"));
        assertEquals(DataMaskingUtils.MASK, masked.get("apiKey"));
        assertEquals(5, masked.get("count"));

        assertInstanceOf(Map.class, masked.get("payment"));
        Map<?, ?> maskedNested = (Map<?, ?>) masked.get("payment");
        assertEquals(DataMaskingUtils.MASK, maskedNested.get("cvv"));
        assertEquals("John Doe", maskedNested.get("cardHolder"));
    }

    @Test
    void testMaskJson() {
        String json =
                "{\"username\":\"alice\",\"password\":\"secretPass\",\"token\":\"xyz123\",\"active\":true}";
        String masked = DataMaskingUtils.maskJson(json);

        assertTrue(masked.contains("\"password\":\"" + DataMaskingUtils.MASK + "\""));
        assertTrue(masked.contains("\"token\":\"" + DataMaskingUtils.MASK + "\""));
        assertTrue(masked.contains("\"username\":\"alice\""));
        assertTrue(masked.contains("\"active\":true"));
    }

    @Test
    void testMaskHeader() {
        assertEquals(
                DataMaskingUtils.MASK,
                DataMaskingUtils.maskHeader("Authorization", "Bearer eyJhbGciOi..."));
        assertEquals(DataMaskingUtils.MASK, DataMaskingUtils.maskHeader("X-Api-Key", "my-key"));
        assertEquals(
                "application/json",
                DataMaskingUtils.maskHeader("Content-Type", "application/json"));
        assertNull(DataMaskingUtils.maskHeader("X-Empty", null));
    }

    @Test
    void testRedactText() {
        String text = "Call with Bearer secret-token-value and credit card 4111111111111111";
        String redacted = DataMaskingUtils.redactText(text);

        assertTrue(redacted.contains("Bearer ******"));
        assertTrue(redacted.contains("****-****-****-****"));
        assertFalse(redacted.contains("4111111111111111"));
    }

    @Test
    void testMaskEmail() {
        assertEquals("j***@domain.com", DataMaskingUtils.maskEmail("john@domain.com"));
        assertEquals(DataMaskingUtils.MASK, DataMaskingUtils.maskEmail("invalid-email"));
        assertNull(DataMaskingUtils.maskEmail(null));
    }

    @Test
    void testMaskPhone() {
        assertEquals("******7890", DataMaskingUtils.maskPhone("0987657890"));
        assertEquals("*******7890", DataMaskingUtils.maskPhone("+84 987 65 7890"));
        assertEquals(DataMaskingUtils.MASK, DataMaskingUtils.maskPhone("123"));
    }

    @Test
    void testRedactStackTrace() {
        Exception ex = new RuntimeException("Test exception with Bearer token-xyz");
        String redacted = DataMaskingUtils.redactStackTrace(ex);

        assertNotNull(redacted);
        assertTrue(redacted.contains("Test exception with Bearer ******"));
        assertEquals("", DataMaskingUtils.redactStackTrace(null));
    }
}
