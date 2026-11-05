package com.shareride.common;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.FieldErrorDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaseResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        TraceIdContext.setTraceId("test-trace-id-123");
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
    }

    @Test
    @DisplayName("Should create successful BaseResponse with data")
    void testSuccessResponse() {
        BaseResponse<String> res = BaseResponse.success("payload-data", "OK");

        assertTrue(res.isSuccess());
        assertEquals("SUCCESS", res.getCode());
        assertEquals("OK", res.getMessage());
        assertEquals("payload-data", res.getData());
        assertEquals("test-trace-id-123", res.getTraceId());
        assertNotNull(res.getTimestamp());
        assertNull(res.getErrors());
    }

    @Test
    @DisplayName("Should create error BaseResponse with details")
    void testErrorResponse() {
        List<FieldErrorDetail> errors = List.of(
                FieldErrorDetail.builder().field("phone").message("Phone is invalid").build()
        );

        BaseResponse<Void> res = BaseResponse.error(
                ResponseCode.VALIDATION_FAILED.getCode(),
                "Validation error",
                errors,
                "/api/v1/users"
        );

        assertFalse(res.isSuccess());
        assertEquals(ResponseCode.VALIDATION_FAILED.getCode(), res.getCode());
        assertEquals("Validation error", res.getMessage());
        assertEquals("/api/v1/users", res.getPath());
        assertEquals(1, res.getErrors().size());
        assertEquals("phone", res.getErrors().get(0).getField());
    }

    @Test
    @DisplayName("Should serialize to JSON properly")
    void testJsonSerialization() throws Exception {
        BaseResponse<String> res = BaseResponse.success("data");
        String json = objectMapper.writeValueAsString(res);

        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"code\":\"SUCCESS\""));
        assertTrue(json.contains("\"traceId\":\"test-trace-id-123\""));
        assertFalse(json.contains("\"errors\"")); // non-null omitted
    }
}
