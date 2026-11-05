package com.shareride.common;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.GlobalExceptionHandler;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.response.BaseResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private TemplateMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = new TemplateMessageSource();
        messageSource.init();
        exceptionHandler = new GlobalExceptionHandler(messageSource);
        TraceIdContext.setTraceId("trace-xyz-999");
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException and render localized parameterized message")
    void testResourceNotFoundHandling() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides/99");
        ResourceNotFoundException ex = new ResourceNotFoundException("Ride", 99);

        ResponseEntity<BaseResponse<Void>> response = exceptionHandler.handleBaseException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getCode());
        assertEquals("Could not find Ride with identifier '99'", response.getBody().getMessage());
        assertEquals("/api/v1/rides/99", response.getBody().getPath());
        assertEquals("trace-xyz-999", response.getBody().getTraceId());
    }

    @Test
    @DisplayName("Should handle Japanese locale properly for UnauthorizedException")
    void testJapaneseLocaleException() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile");
        UnauthorizedException ex = new UnauthorizedException();

        ResponseEntity<BaseResponse<Void>> response = exceptionHandler.handleBaseException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
        assertEquals("認証されていないか、セッションの有効期限が切れています", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Should catch unexpected generic Exception and return 500 BaseResponse")
    void testUnhandledGenericException() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides");
        Exception ex = new RuntimeException("Database connection timeout");

        ResponseEntity<BaseResponse<Void>> response = exceptionHandler.handleGeneralException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR.getCode(), response.getBody().getCode());
        assertEquals("Hệ thống đang gặp sự cố, vui lòng thử lại sau", response.getBody().getMessage());
    }
}
