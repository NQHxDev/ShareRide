package com.shareride.common.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.common.filter.TraceIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShareRideStatementInspectorTest {

    private final ShareRideStatementInspector inspector = new ShareRideStatementInspector();

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
    }

    @Test
    @DisplayName("Statement inspector should prepend traceId and spanId comment to SQL")
    void testInspectSql() {
        TraceIdContext.setTraceId("4bf92f3577b34da6a3ce929d0e0e4736");
        TraceIdContext.setSpanId("00f067aa0ba902b7");

        String query = "SELECT * FROM users WHERE email = ?";
        String result = inspector.inspect(query);

        assertNotNull(result);
        assertTrue(
                result.startsWith(
                        "/* traceId=4bf92f3577b34da6a3ce929d0e0e4736, spanId=00f067aa0ba902b7 */"));
        assertTrue(result.contains("SELECT * FROM users WHERE email = ?"));
    }
}
