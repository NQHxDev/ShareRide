package com.shareride.common.exception;

import com.github.f4b6a3.uuid.UuidCreator;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.LocaleContextHolderUtil;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.audit.AuditLogService;
import com.shareride.common.logging.audit.DefaultAuditLogService;
import com.shareride.common.logging.masking.DataMaskingUtils;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.FieldErrorDetail;
import com.shareride.common.versioning.UnsupportedApiVersionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Global REST exception handler mapping all exceptions to standard BaseResponse format, enriching
 * error logs with errorId, userId, endpoint, and sanitized parameters, and dispatching security
 * audit logs and observability metrics.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TemplateMessageSource messageSource;
    private final ShareRideMetrics metrics;
    private final AuditLogService auditLogService;

    public GlobalExceptionHandler(TemplateMessageSource messageSource) {
        this(messageSource, (ShareRideMetrics) null, null);
    }

    public GlobalExceptionHandler(TemplateMessageSource messageSource, ShareRideMetrics metrics) {
        this(messageSource, metrics, null);
    }

    public GlobalExceptionHandler(
            TemplateMessageSource messageSource,
            ShareRideMetrics metrics,
            AuditLogService auditLogService) {
        this.messageSource = messageSource;
        this.metrics = metrics;
        this.auditLogService =
                auditLogService != null ? auditLogService : new DefaultAuditLogService();
    }

    public GlobalExceptionHandler(
            TemplateMessageSource messageSource,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this(
                messageSource,
                metricsProvider != null ? metricsProvider.getIfAvailable() : null,
                auditLogServiceProvider != null ? auditLogServiceProvider.getIfAvailable() : null);
    }

    @Autowired
    public GlobalExceptionHandler(
            ObjectProvider<TemplateMessageSource> messageSourceProvider,
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<AuditLogService> auditLogServiceProvider) {
        TemplateMessageSource tms =
                messageSourceProvider != null ? messageSourceProvider.getIfAvailable() : null;
        if (tms == null) {
            tms = com.shareride.common.i18n.MessageResolverHolder.getTemplateMessageSource();
            if (tms == null) {
                tms = new TemplateMessageSource();
                tms.init();
            }
        }
        this.messageSource = tms;
        this.metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        this.auditLogService =
                (auditLogServiceProvider != null
                                && auditLogServiceProvider.getIfAvailable() != null)
                        ? auditLogServiceProvider.getIfAvailable()
                        : new DefaultAuditLogService();
    }

    private String initErrorContext() {
        String errorId = UuidCreator.getTimeOrderedEpoch().toString();
        MDC.put(MdcConstants.ERROR_ID, errorId);
        return errorId;
    }

    private void cleanupErrorContext() {
        MDC.remove(MdcConstants.ERROR_ID);
    }

    private Map<String, Object> extractSanitizedParams(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        return DataMaskingUtils.maskParams(request.getParameterMap());
    }

    private void recordExceptionMetric(String code, String exceptionType) {
        if (metrics != null) {
            metrics.incrementCounter(
                    "exceptions.total",
                    "code",
                    code != null ? code : "UNKNOWN",
                    "type",
                    exceptionType != null ? exceptionType : "UNKNOWN");
        }
    }

    /** Handle custom business exceptions derived from BaseException. */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<BaseResponse<Void>> handleBaseException(
            BaseException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            String message = ex.getMessage();
            String userId = UserContextHolder.getCurrentUserId().orElse("anonymous");
            String endpoint = request.getRequestURI();
            Map<String, Object> sanitizedParams = extractSanitizedParams(request);

            // If explicit custom message was not passed, or matches code, resolve from
            // template
            String resolved = messageSource.getMessage(ex.getCode(), ex.getParams(), locale);
            if (resolved != null && !resolved.equals(ex.getCode())) {
                message = resolved;
            }
            log.warn(
                    "Business error [errorId={}, code={}, userId={}, endpoint={}, params={}]: {}",
                    errorId,
                    ex.getCode(),
                    userId,
                    endpoint,
                    sanitizedParams,
                    message);
            recordExceptionMetric(ex.getCode(), ex.getClass().getSimpleName());

            // Record security audit metrics and events for auth/rate-limit exceptions
            if (ex instanceof UnauthorizedException) {
                if (metrics != null) {
                    metrics.recordAuthFailure(
                            "SESSION_OR_TOKEN", ex.getCode(), request.getRemoteAddr());
                }
                auditLogService.logAuthentication(
                        userId,
                        "UNAUTHORIZED",
                        request.getRemoteAddr(),
                        request.getHeader("X-Device-Id"),
                        Map.of("errorId", errorId, "code", ex.getCode()));
            } else if (ex instanceof ForbiddenException) {
                if (metrics != null) {
                    metrics.recordAccessDenied(
                            request.getRequestURI(), "ROLE_RESTRICTED", request.getMethod());
                }
                auditLogService.logAuthorization(
                        userId,
                        "ACCESS_DENIED",
                        request.getRequestURI(),
                        request.getMethod(),
                        Map.of("errorId", errorId, "code", ex.getCode()));
            } else if (ex instanceof UnsupportedApiVersionException unsupportedEx) {
                if (metrics != null) {
                    metrics.recordApiVersionRequest(
                            unsupportedEx.getRequestedVersion(), "UNSUPPORTED");
                }
            } else if (ex instanceof RateLimitExceededException) {
                if (metrics != null) {
                    metrics.recordRateLimitExceeded(
                            request.getRemoteAddr(), request.getRequestURI(), "API_RATE_LIMIT");
                }
                auditLogService.logSecurityEvent(
                        com.shareride.common.logging.audit.SecurityAuditEvent.builder()
                                .eventType("RATE_LIMIT_EXCEEDED")
                                .userId(userId)
                                .clientIp(request.getRemoteAddr())
                                .resource(request.getRequestURI())
                                .action(request.getMethod())
                                .status("FAILURE")
                                .traceId(TraceIdContext.getTraceId())
                                .details(Map.of("errorId", errorId, "code", ex.getCode()))
                                .build());
            }

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ex.getCode())
                            .message(message)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(endpoint)
                            .build();

            return ResponseEntity.status(ex.getHttpStatus()).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle @Valid / @Validated argument validation failures on request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            List<FieldErrorDetail> errorDetails =
                    FieldErrorDetail.fromBindingResult(ex.getBindingResult());

            String message =
                    messageSource.getMessage(ResponseCode.VALIDATION_FAILED.getCode(), locale);
            recordExceptionMetric(
                    ResponseCode.VALIDATION_FAILED.getCode(), ex.getClass().getSimpleName());

            String userId = UserContextHolder.getCurrentUserId().orElse("anonymous");
            log.warn(
                    "Validation error [errorId={}, userId={}, endpoint={}, errorsCount={}]",
                    errorId,
                    userId,
                    request.getRequestURI(),
                    errorDetails.size());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.VALIDATION_FAILED.getCode())
                            .message(message)
                            .errors(errorDetails)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle constraint violations on query params or path variables. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            List<FieldErrorDetail> errorDetails = new ArrayList<>();

            for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
                errorDetails.add(
                        FieldErrorDetail.builder()
                                .field(
                                        violation.getPropertyPath() != null
                                                ? violation.getPropertyPath().toString()
                                                : "")
                                .rejectedValue(violation.getInvalidValue())
                                .code(
                                        violation.getConstraintDescriptor() != null
                                                ? violation
                                                        .getConstraintDescriptor()
                                                        .getAnnotation()
                                                        .annotationType()
                                                        .getSimpleName()
                                                : null)
                                .message(violation.getMessage())
                                .build());
            }

            String message =
                    messageSource.getMessage(ResponseCode.VALIDATION_FAILED.getCode(), locale);
            recordExceptionMetric(
                    ResponseCode.VALIDATION_FAILED.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.VALIDATION_FAILED.getCode())
                            .message(message)
                            .errors(errorDetails)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle missing required query parameters. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            Map<String, Object> params = Map.of("field", ex.getParameterName());
            String msg = messageSource.getMessage("FIELD_REQUIRED", params, locale);
            recordExceptionMetric(
                    ResponseCode.BAD_REQUEST.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.BAD_REQUEST.getCode())
                            .message(msg)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle parameter type mismatch. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            Map<String, Object> params =
                    Map.of(
                            "field", ex.getName(),
                            "value", String.valueOf(ex.getValue()));
            String msg = messageSource.getMessage("FIELD_INVALID", params, locale);
            recordExceptionMetric(
                    ResponseCode.BAD_REQUEST.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.BAD_REQUEST.getCode())
                            .message(msg)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle unreadable HTTP body (malformed JSON, etc.). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            String message = messageSource.getMessage(ResponseCode.BAD_REQUEST.getCode(), locale);
            recordExceptionMetric(
                    ResponseCode.BAD_REQUEST.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.BAD_REQUEST.getCode())
                            .message(message)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle HTTP 405 Method Not Allowed. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            String message =
                    messageSource.getMessage(ResponseCode.METHOD_NOT_ALLOWED.getCode(), locale);
            recordExceptionMetric(
                    ResponseCode.METHOD_NOT_ALLOWED.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.METHOD_NOT_ALLOWED.getCode())
                            .message(message)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle HTTP 415 Media Type Not Supported. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            String message = messageSource.getMessage(ResponseCode.BAD_REQUEST.getCode(), locale);
            recordExceptionMetric(
                    ResponseCode.BAD_REQUEST.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.BAD_REQUEST.getCode())
                            .message(message)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Handle 404 No Handler Found. */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            Locale locale = LocaleContextHolderUtil.getLocale();
            String message = messageSource.getMessage(ResponseCode.NOT_FOUND.getCode(), locale);
            recordExceptionMetric(ResponseCode.NOT_FOUND.getCode(), ex.getClass().getSimpleName());

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.NOT_FOUND.getCode())
                            .message(message)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        } finally {
            cleanupErrorContext();
        }
    }

    /** Fallback for any unhandled unexpected exceptions. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleGeneralException(
            Exception ex, HttpServletRequest request) {
        String errorId = initErrorContext();
        try {
            String userId = UserContextHolder.getCurrentUserId().orElse("anonymous");
            String endpoint = request.getRequestURI();
            Map<String, Object> sanitizedParams = extractSanitizedParams(request);

            log.error(
                    "Unhandled error [errorId={}, userId={}, endpoint={}, params={}]: ",
                    errorId,
                    userId,
                    endpoint,
                    sanitizedParams,
                    ex);
            recordExceptionMetric(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCode(), ex.getClass().getSimpleName());

            Locale locale = LocaleContextHolderUtil.getLocale();
            String message =
                    messageSource.getMessage(ResponseCode.INTERNAL_SERVER_ERROR.getCode(), locale);

            BaseResponse<Void> body =
                    BaseResponse.<Void>builder()
                            .success(false)
                            .code(ResponseCode.INTERNAL_SERVER_ERROR.getCode())
                            .message(message)
                            .errorId(errorId)
                            .traceId(TraceIdContext.getTraceId())
                            .timestamp(Instant.now())
                            .path(endpoint)
                            .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        } finally {
            cleanupErrorContext();
        }
    }
}
