package com.shareride.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.GlobalExceptionHandler;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.response.BaseResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private TemplateMessageSource messageSource;
    private MeterRegistry meterRegistry;
    private ShareRideMetrics metrics;

    @BeforeEach
    void setUp() {
        messageSource = new TemplateMessageSource();
        messageSource.init();

        meterRegistry = new SimpleMeterRegistry();
        metrics = new ShareRideMetrics(meterRegistry);

        exceptionHandler = new GlobalExceptionHandler(messageSource, metrics);
        TraceIdContext.setTraceId("trace-xyz-999");
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName(
            "Should handle ResourceNotFoundException and render localized parameterized message and"
                    + " record metric")
    void testResourceNotFoundHandling() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides/99");
        ResourceNotFoundException ex = new ResourceNotFoundException("Ride", 99);

        ResponseEntity<BaseResponse<Void>> response =
                exceptionHandler.handleBaseException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getCode());
        assertEquals("Could not find Ride with identifier '99'", response.getBody().getMessage());
        assertEquals("/api/v1/rides/99", response.getBody().getPath());
        assertEquals("trace-xyz-999", response.getBody().getTraceId());

        // Verify exception metric was recorded
        double count =
                meterRegistry
                        .get("shareride.exceptions.total")
                        .tag("code", "RESOURCE_NOT_FOUND")
                        .tag("type", "ResourceNotFoundException")
                        .counter()
                        .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("Should handle Japanese locale properly for UnauthorizedException")
    void testJapaneseLocaleException() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile");
        UnauthorizedException ex = new UnauthorizedException();

        ResponseEntity<BaseResponse<Void>> response =
                exceptionHandler.handleBaseException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
        assertEquals("認証されていないか、セッションの有効期限が切れています", response.getBody().getMessage());

        double count =
                meterRegistry
                        .get("shareride.exceptions.total")
                        .tag("code", "UNAUTHORIZED")
                        .tag("type", "UnauthorizedException")
                        .counter()
                        .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("Should catch unexpected generic Exception and return 500 BaseResponse")
    void testUnhandledGenericException() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides");
        Exception ex = new RuntimeException("Database connection timeout");

        ResponseEntity<BaseResponse<Void>> response =
                exceptionHandler.handleGeneralException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR.getCode(), response.getBody().getCode());
        assertEquals(
                "Hệ thống đang gặp sự cố, vui lòng thử lại sau", response.getBody().getMessage());

        double count =
                meterRegistry
                        .get("shareride.exceptions.total")
                        .tag("code", ResponseCode.INTERNAL_SERVER_ERROR.getCode())
                        .tag("type", "RuntimeException")
                        .counter()
                        .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("Should handle Spring Security AccessDeniedException (403 Forbidden)")
    void testAccessDeniedExceptionHandling() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/dashboard");
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<BaseResponse<Void>> response =
                exceptionHandler.handleAccessDeniedException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("FORBIDDEN", response.getBody().getCode());
        assertEquals(
                "You do not have permission to access this resource",
                response.getBody().getMessage());
        assertEquals("/api/v1/admin/dashboard", response.getBody().getPath());
        assertEquals("trace-xyz-999", response.getBody().getTraceId());

        double count =
                meterRegistry
                        .get("shareride.exceptions.total")
                        .tag("code", "FORBIDDEN")
                        .tag("type", "AccessDeniedException")
                        .counter()
                        .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("Should handle Spring Security AuthenticationException (401 Unauthorized)")
    void testAuthenticationExceptionHandling() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/token");
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<BaseResponse<Void>> response =
                exceptionHandler.handleAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
        assertEquals("Unauthorized access or session has expired", response.getBody().getMessage());
        assertEquals("/api/v1/auth/token", response.getBody().getPath());
        assertEquals("trace-xyz-999", response.getBody().getTraceId());

        double count =
                meterRegistry
                        .get("shareride.exceptions.total")
                        .tag("code", "UNAUTHORIZED")
                        .tag("type", "BadCredentialsException")
                        .counter()
                        .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("Should use fallback message when messageSource has no entry for FORBIDDEN")
    void testAccessDeniedExceptionFallbackMessage() {
        TemplateMessageSource mockMessageSource = mock(TemplateMessageSource.class);
        when(mockMessageSource.getMessage(eq("FORBIDDEN"), any(Locale.class)))
                .thenReturn("FORBIDDEN");

        GlobalExceptionHandler customHandler =
                new GlobalExceptionHandler(mockMessageSource, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/secret");
        AccessDeniedException ex = new AccessDeniedException("Forbidden");

        ResponseEntity<BaseResponse<Void>> response =
                customHandler.handleAccessDeniedException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Không có quyền truy cập tài nguyên này", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Should use fallback message when messageSource has no entry for UNAUTHORIZED")
    void testAuthenticationExceptionFallbackMessage() {
        TemplateMessageSource mockMessageSource = mock(TemplateMessageSource.class);
        when(mockMessageSource.getMessage(eq("UNAUTHORIZED"), any(Locale.class)))
                .thenReturn("UNAUTHORIZED");

        GlobalExceptionHandler customHandler =
                new GlobalExceptionHandler(mockMessageSource, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/verify");
        BadCredentialsException ex = new BadCredentialsException("Auth error");

        ResponseEntity<BaseResponse<Void>> response =
                customHandler.handleAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Yêu cầu chưa được xác thực hoặc Access Token không hợp lệ",
                response.getBody().getMessage());
    }
}
