package com.shareride.common.exception;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.LocaleContextHolderUtil;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.*;

/**
 * Global REST exception handler mapping all exceptions to standard BaseResponse
 * format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

   private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

   private final TemplateMessageSource messageSource;

   public GlobalExceptionHandler(TemplateMessageSource messageSource) {
      this.messageSource = messageSource;
   }

   /**
    * Handle custom business exceptions derived from BaseException.
    */
   @ExceptionHandler(BaseException.class)
   public ResponseEntity<BaseResponse<Void>> handleBaseException(BaseException ex, HttpServletRequest request) {
      Locale locale = LocaleContextHolderUtil.getLocale();
      String message = ex.getMessage();

      // If explicit custom message was not passed, or matches code, resolve from
      // template
      String resolved = messageSource.getMessage(ex.getCode(), ex.getParams(), locale);
      if (resolved != null && !resolved.equals(ex.getCode())) {
         message = resolved;
      }

      log.warn("Business exception [{}]: {}", ex.getCode(), message);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ex.getCode())
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(ex.getHttpStatus()).body(body);
   }

   /**
    * Handle @Valid / @Validated argument validation failures on request bodies.
    */
   @ExceptionHandler(MethodArgumentNotValidException.class)
   public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(
         MethodArgumentNotValidException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      List<FieldErrorDetail> errorDetails = new ArrayList<>();

      for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
         errorDetails.add(FieldErrorDetail.builder()
               .field(fieldError.getField())
               .rejectedValue(fieldError.getRejectedValue())
               .code(fieldError.getCode())
               .message(fieldError.getDefaultMessage())
               .build());
      }

      String message = messageSource.getMessage(ResponseCode.VALIDATION_FAILED.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.VALIDATION_FAILED.getCode())
            .message(message)
            .errors(errorDetails)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
   }

   /**
    * Handle constraint violations on query params or path variables.
    */
   @ExceptionHandler(ConstraintViolationException.class)
   public ResponseEntity<BaseResponse<Void>> handleConstraintViolation(
         ConstraintViolationException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      List<FieldErrorDetail> errorDetails = new ArrayList<>();

      for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
         errorDetails.add(FieldErrorDetail.builder()
               .field(violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "")
               .rejectedValue(violation.getInvalidValue())
               .code(violation.getConstraintDescriptor() != null
                     ? violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                     : null)
               .message(violation.getMessage())
               .build());
      }

      String message = messageSource.getMessage(ResponseCode.VALIDATION_FAILED.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.VALIDATION_FAILED.getCode())
            .message(message)
            .errors(errorDetails)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
   }

   /**
    * Handle missing required query parameters.
    */
   @ExceptionHandler(MissingServletRequestParameterException.class)
   public ResponseEntity<BaseResponse<Void>> handleMissingServletRequestParameter(
         MissingServletRequestParameterException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      Map<String, Object> params = Map.of("field", ex.getParameterName());
      String msg = messageSource.getMessage("FIELD_REQUIRED", params, locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.BAD_REQUEST.getCode())
            .message(msg)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
   }

   /**
    * Handle parameter type mismatch.
    */
   @ExceptionHandler(MethodArgumentTypeMismatchException.class)
   public ResponseEntity<BaseResponse<Void>> handleMethodArgumentTypeMismatch(
         MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      Map<String, Object> params = Map.of(
            "field", ex.getName(),
            "value", String.valueOf(ex.getValue()));
      String msg = messageSource.getMessage("FIELD_INVALID", params, locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.BAD_REQUEST.getCode())
            .message(msg)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
   }

   /**
    * Handle unreadable HTTP body (malformed JSON, etc.).
    */
   @ExceptionHandler(HttpMessageNotReadableException.class)
   public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadable(
         HttpMessageNotReadableException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      String message = messageSource.getMessage(ResponseCode.BAD_REQUEST.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.BAD_REQUEST.getCode())
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
   }

   /**
    * Handle HTTP 405 Method Not Allowed.
    */
   @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
   public ResponseEntity<BaseResponse<Void>> handleMethodNotSupported(
         HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      String message = messageSource.getMessage(ResponseCode.METHOD_NOT_ALLOWED.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.METHOD_NOT_ALLOWED.getCode())
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
   }

   /**
    * Handle HTTP 415 Media Type Not Supported.
    */
   @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
   public ResponseEntity<BaseResponse<Void>> handleMediaTypeNotSupported(
         HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      String message = messageSource.getMessage(ResponseCode.BAD_REQUEST.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.BAD_REQUEST.getCode())
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
   }

   /**
    * Handle 404 No Handler Found.
    */
   @ExceptionHandler(NoHandlerFoundException.class)
   public ResponseEntity<BaseResponse<Void>> handleNoHandlerFound(
         NoHandlerFoundException ex, HttpServletRequest request) {

      Locale locale = LocaleContextHolderUtil.getLocale();
      String message = messageSource.getMessage(ResponseCode.NOT_FOUND.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.NOT_FOUND.getCode())
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
   }

   /**
    * Fallback for any unhandled unexpected exceptions.
    */
   @ExceptionHandler(Exception.class)
   public ResponseEntity<BaseResponse<Void>> handleGeneralException(
         Exception ex, HttpServletRequest request) {

      log.error("Unhandled unexpected exception on path [{}]: ", request.getRequestURI(), ex);

      Locale locale = LocaleContextHolderUtil.getLocale();
      String message = messageSource.getMessage(ResponseCode.INTERNAL_SERVER_ERROR.getCode(), locale);

      BaseResponse<Void> body = BaseResponse.<Void>builder()
            .success(false)
            .code(ResponseCode.INTERNAL_SERVER_ERROR.getCode())
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
   }
}
