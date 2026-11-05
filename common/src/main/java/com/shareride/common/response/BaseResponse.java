package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.filter.TraceIdContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Standard API Response wrapper across all ShareRide Microservices.
 *
 * @param <T> Payload data type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> implements Serializable {

   private boolean success;
   private String code;
   private String message;
   private T data;
   private List<FieldErrorDetail> errors;
   private String traceId;

   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
   private Instant timestamp;

   private String path;

   // --- Fluent Factory Methods ---

   public static <T> BaseResponse<T> success(T data) {
      return BaseResponse.<T>builder()
            .success(true)
            .code(ResponseCode.SUCCESS.getCode())
            .message("Success")
            .data(data)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .build();
   }

   public static <T> BaseResponse<T> success(T data, String message) {
      return BaseResponse.<T>builder()
            .success(true)
            .code(ResponseCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .build();
   }

   public static <T> BaseResponse<T> success(String code, String message, T data) {
      return BaseResponse.<T>builder()
            .success(true)
            .code(code)
            .message(message)
            .data(data)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .build();
   }

   public static <T> BaseResponse<T> emptySuccess() {
      return success(null);
   }

   public static <T> BaseResponse<T> error(String code, String message) {
      return BaseResponse.<T>builder()
            .success(false)
            .code(code)
            .message(message)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .build();
   }

   public static <T> BaseResponse<T> error(ResponseCode responseCode, String message) {
      return error(responseCode.getCode(), message);
   }

   public static <T> BaseResponse<T> error(String code, String message, List<FieldErrorDetail> errors) {
      return BaseResponse.<T>builder()
            .success(false)
            .code(code)
            .message(message)
            .errors(errors)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .build();
   }

   public static <T> BaseResponse<T> error(String code, String message, List<FieldErrorDetail> errors, String path) {
      return BaseResponse.<T>builder()
            .success(false)
            .code(code)
            .message(message)
            .errors(errors)
            .path(path)
            .traceId(TraceIdContext.getTraceId())
            .timestamp(Instant.now())
            .build();
   }

}
