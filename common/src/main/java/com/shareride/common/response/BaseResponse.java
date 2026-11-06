package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.LocaleContextHolderUtil;
import com.shareride.common.i18n.MessageResolverHolder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Enterprise standard API Response wrapper across all ShareRide Microservices.
 * Supports pagination, multi-level responses, nested error reporting, metadata,
 * message localization, and an advanced fluent builder.
 *
 * @param <T> Payload data type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> implements Serializable {

   private boolean success;
   private ResponseLevel level;
   private String code;
   private String message;
   private T data;
   private PageInfo pagination;
   private ResponseMetadata metadata;
   private List<FieldErrorDetail> errors;
   private List<String> warnings;
   private String traceId;
   private String errorId;

   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
   private Instant timestamp;

   private String path;

   // --- Fluent Factory Methods: Success ---

   public static <T> BaseResponse<T> success(T data) {
      return success(data, "Success");
   }

   public static <T> BaseResponse<T> success(T data, String message) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.SUCCESS)
            .code(ResponseCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> success(String code, String message, T data) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.SUCCESS)
            .code(code)
            .message(message)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> emptySuccess() {
      return success(null);
   }

   public static <T> BaseResponse<T> successLocalized(String messageKey, T data) {
      return successLocalized(messageKey, Map.of(), data);
   }

   public static <T> BaseResponse<T> successLocalized(String messageKey, Map<String, Object> params, T data) {
      String resolved = MessageResolverHolder.getMessage(messageKey, params, LocaleContextHolderUtil.getLocale());
      return BaseResponse.<T>builder()
            .level(ResponseLevel.SUCCESS)
            .code(ResponseCode.SUCCESS.getCode())
            .message(resolved)
            .data(data)
            .build();
   }

   // --- Fluent Factory Methods: Pagination ---

   public static <T> BaseResponse<T> paginate(T data, PageInfo pagination) {
      return paginate(data, pagination, "Success");
   }

   public static <T> BaseResponse<T> paginate(T data, PageInfo pagination, String message) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.SUCCESS)
            .code(ResponseCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .pagination(pagination)
            .build();
   }

   public static <T> BaseResponse<T> paginate(T data, int page, int size, long totalElements) {
      if (page < 0 || size <= 0 || totalElements < 0) {
         throw new IllegalArgumentException("Invalid pagination parameters");
      }
      return paginate(data, PageInfo.of(page, size, totalElements));
   }

   public static <T> BaseResponse<T> paginate(T data, int page, int size, long totalElements, String message) {
      if (page < 0 || size <= 0 || totalElements < 0) {
         throw new IllegalArgumentException("Invalid pagination parameters");
      }
      return paginate(data, PageInfo.of(page, size, totalElements), message);
   }

   // --- Fluent Factory Methods: Info & Warning ---

   public static <T> BaseResponse<T> info(T data, String message) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.INFO)
            .code(ResponseCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> info(String code, String message, T data) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.INFO)
            .code(code)
            .message(message)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> infoLocalized(ResponseCode responseCode, T data) {
      if (responseCode == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      String resolved = MessageResolverHolder.getMessage(responseCode.getCode(), LocaleContextHolderUtil.getLocale());
      return BaseResponse.<T>builder()
            .level(ResponseLevel.INFO)
            .code(responseCode.getCode())
            .message(resolved)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> warning(T data, String message) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.WARNING)
            .code(ResponseCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> warning(ResponseCode code, String message, T data) {
      if (code == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      return BaseResponse.<T>builder()
            .level(ResponseLevel.WARNING)
            .code(code.getCode())
            .message(message)
            .data(data)
            .build();
   }

   public static <T> BaseResponse<T> warning(String code, String message, T data) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.WARNING)
            .code(code)
            .message(message)
            .data(data)
            .build();
   }

   @Deprecated
   public static <T> BaseResponse<T> warningWithCode(String code, String message, T data) {
      return warning(code, message, data);
   }

   public static <T> BaseResponse<T> warning(T data, List<String> warnings) {
      return warning(data, "Warning", warnings);
   }

   public static <T> BaseResponse<T> warning(T data, String message, List<String> warnings) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.WARNING)
            .code(ResponseCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .warnings(warnings)
            .build();
   }

   public static <T> BaseResponse<T> warningLocalized(ResponseCode responseCode, T data) {
      if (responseCode == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      String resolved = MessageResolverHolder.getMessage(responseCode.getCode(), LocaleContextHolderUtil.getLocale());
      return BaseResponse.<T>builder()
            .level(ResponseLevel.WARNING)
            .code(responseCode.getCode())
            .message(resolved)
            .data(data)
            .build();
   }

   // --- Fluent Factory Methods: Error ---

   public static <T> BaseResponse<T> error(String code, String message) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.ERROR)
            .code(code)
            .message(message)
            .build();
   }

   public static <T> BaseResponse<T> error(ResponseCode responseCode, String message) {
      if (responseCode == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      return error(responseCode.getCode(), message);
   }

   public static <T> BaseResponse<T> error(String code, String message, List<FieldErrorDetail> errors) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.ERROR)
            .code(code)
            .message(message)
            .errors(errors)
            .build();
   }

   public static <T> BaseResponse<T> error(String code, String message, List<FieldErrorDetail> errors, String path) {
      return BaseResponse.<T>builder()
            .level(ResponseLevel.ERROR)
            .code(code)
            .message(message)
            .errors(errors)
            .path(path)
            .build();
   }

   public static <T> BaseResponse<T> errorLocalized(ResponseCode responseCode) {
      if (responseCode == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      return errorLocalized(responseCode, Map.of());
   }

   public static <T> BaseResponse<T> errorLocalized(ResponseCode responseCode, Map<String, Object> params) {
      if (responseCode == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      String resolved = MessageResolverHolder.getMessage(responseCode.getCode(), params,
            LocaleContextHolderUtil.getLocale());
      return BaseResponse.<T>builder()
            .level(ResponseLevel.ERROR)
            .code(responseCode.getCode())
            .message(resolved)
            .build();
   }

   public static <T> BaseResponse<T> errorLocalized(String code, String messageKey, Map<String, Object> params) {
      String resolved = MessageResolverHolder.getMessage(messageKey, params, LocaleContextHolderUtil.getLocale());
      return BaseResponse.<T>builder()
            .level(ResponseLevel.ERROR)
            .code(code)
            .message(resolved)
            .build();
   }

   public static <T> BaseResponse<T> errorLocalized(ResponseCode responseCode, List<FieldErrorDetail> errors) {
      if (responseCode == null) {
         throw new IllegalArgumentException("ResponseCode cannot be null");
      }
      String resolved = MessageResolverHolder.getMessage(responseCode.getCode(), LocaleContextHolderUtil.getLocale());
      return BaseResponse.<T>builder()
            .level(ResponseLevel.ERROR)
            .code(responseCode.getCode())
            .message(resolved)
            .errors(errors)
            .build();
   }

   // --- Status check helpers ---

   @JsonIgnore
   public boolean isWarning() {
      return this.level == ResponseLevel.WARNING;
   }

   @JsonIgnore
   public boolean isInfo() {
      return this.level == ResponseLevel.INFO;
   }

   @JsonIgnore
   public boolean isError() {
      return !this.success || this.level == ResponseLevel.ERROR;
   }

   // --- Utility Methods ---

   @JsonIgnore
   public boolean hasErrors() {
      return errors != null && !errors.isEmpty();
   }

   @JsonIgnore
   public boolean hasWarnings() {
      return warnings != null && !warnings.isEmpty();
   }

   @JsonIgnore
   public int getTotalErrorCount() {
      if (errors == null)
         return 0;
      return errors.stream()
            .mapToInt(e -> 1 + (e.hasNestedErrors() ? e.getNestedErrors().size() : 0))
            .sum();
   }

   // --- Copy and Transform Methods ---

   public <R> BaseResponse<R> map(Function<T, R> mapper) {
      if (mapper == null) {
         throw new IllegalArgumentException("mapper cannot be null");
      }
      R mappedData = this.data != null ? mapper.apply(this.data) : null;
      return BaseResponse.<R>builder()
            .success(this.success)
            .level(this.level)
            .code(this.code)
            .message(this.message)
            .data(mappedData)
            .pagination(this.pagination)
            .metadata(this.metadata)
            .errors(this.errors != null ? new ArrayList<>(this.errors) : null)
            .warnings(this.warnings != null ? new ArrayList<>(this.warnings) : null)
            .traceId(this.traceId)
            .errorId(this.errorId)
            .timestamp(this.timestamp)
            .path(this.path)
            .build();
   }

   public BaseResponseBuilder<T> toBuilder() {
      BaseResponseBuilder<T> builder = new BaseResponseBuilder<>();
      builder.success = this.success;
      builder.level = this.level;
      builder.code = this.code;
      builder.message = this.message;
      builder.data = this.data;
      builder.pagination = this.pagination;
      builder.metadata = this.metadata;
      builder.errors = this.errors != null ? new ArrayList<>(this.errors) : null;
      builder.warnings = this.warnings != null ? new ArrayList<>(this.warnings) : null;
      builder.traceId = this.traceId;
      builder.errorId = this.errorId;
      builder.timestamp = this.timestamp;
      builder.path = this.path;
      return builder;
   }

   // --- Advanced Builder ---

   public static <T> BaseResponseBuilder<T> builder() {
      return new BaseResponseBuilder<>();
   }

   public static class BaseResponseBuilder<T> {
      private Boolean success;
      private ResponseLevel level;
      private String code;
      private String message;
      private T data;
      private PageInfo pagination;
      private ResponseMetadata metadata;
      private List<FieldErrorDetail> errors;
      private List<String> warnings;
      private String traceId;
      private String errorId;
      private Instant timestamp;
      private String path;

      public BaseResponseBuilder<T> success(boolean success) {
         this.success = success;
         if (this.level == null) {
            this.level = success ? ResponseLevel.SUCCESS : ResponseLevel.ERROR;
         }
         return this;
      }

      public BaseResponseBuilder<T> level(ResponseLevel level) {
         this.level = level;
         if (level != null && this.success == null) {
            this.success = (level != ResponseLevel.ERROR);
         }
         return this;
      }

      public BaseResponseBuilder<T> status(ResponseLevel level) {
         return level(level);
      }

      public BaseResponseBuilder<T> code(String code) {
         this.code = code;
         return this;
      }

      public BaseResponseBuilder<T> code(ResponseCode responseCode) {
         this.code = (responseCode != null) ? responseCode.getCode() : null;
         return this;
      }

      public BaseResponseBuilder<T> message(String message) {
         this.message = message;
         return this;
      }

      public BaseResponseBuilder<T> messageKey(String messageKey) {
         return messageKey(messageKey, Map.of(), LocaleContextHolderUtil.getLocale());
      }

      public BaseResponseBuilder<T> messageKey(String messageKey, Map<String, Object> params) {
         return messageKey(messageKey, params, LocaleContextHolderUtil.getLocale());
      }

      public BaseResponseBuilder<T> messageKey(String messageKey, Map<String, Object> params, Locale locale) {
         this.message = MessageResolverHolder.getMessage(messageKey, params, locale);
         return this;
      }

      public BaseResponseBuilder<T> fromResponseCode(ResponseCode responseCode) {
         return fromResponseCode(responseCode, Map.of(), LocaleContextHolderUtil.getLocale());
      }

      public BaseResponseBuilder<T> fromResponseCode(ResponseCode responseCode, Map<String, Object> params) {
         return fromResponseCode(responseCode, params, LocaleContextHolderUtil.getLocale());
      }

      public BaseResponseBuilder<T> fromResponseCode(ResponseCode responseCode, Map<String, Object> params,
            Locale locale) {
         if (responseCode != null) {
            this.code = responseCode.getCode();
            this.message = MessageResolverHolder.getMessage(responseCode.getCode(), params, locale);
         }
         return this;
      }

      @Deprecated
      public BaseResponseBuilder<T> codeAndMessage(ResponseCode responseCode) {
         return fromResponseCode(responseCode);
      }

      @Deprecated
      public BaseResponseBuilder<T> codeAndMessage(ResponseCode responseCode, Map<String, Object> params) {
         return fromResponseCode(responseCode, params);
      }

      @Deprecated
      public BaseResponseBuilder<T> codeAndMessage(ResponseCode responseCode, Map<String, Object> params,
            Locale locale) {
         return fromResponseCode(responseCode, params, locale);
      }

      public BaseResponseBuilder<T> data(T data) {
         this.data = data;
         return this;
      }

      public BaseResponseBuilder<T> pagination(PageInfo pagination) {
         this.pagination = pagination;
         return this;
      }

      public BaseResponseBuilder<T> pagination(int page, int size, long totalElements) {
         if (page < 0 || size <= 0 || totalElements < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
         }
         this.pagination = PageInfo.of(page, size, totalElements);
         return this;
      }

      public BaseResponseBuilder<T> metadata(ResponseMetadata metadata) {
         this.metadata = metadata;
         return this;
      }

      public BaseResponseBuilder<T> metadata(Consumer<ResponseMetadata> consumer) {
         if (this.metadata == null) {
            this.metadata = new ResponseMetadata();
         }
         consumer.accept(this.metadata);
         return this;
      }

      public BaseResponseBuilder<T> duration(Long durationMs) {
         ensureMetadata().setDurationMs(durationMs);
         return this;
      }

      public BaseResponseBuilder<T> apiVersion(String apiVersion) {
         ensureMetadata().setApiVersion(apiVersion);
         return this;
      }

      public BaseResponseBuilder<T> environment(String environment) {
         ensureMetadata().setEnvironment(environment);
         return this;
      }

      public BaseResponseBuilder<T> attribute(String key, Object value) {
         ensureMetadata().addAttribute(key, value);
         return this;
      }

      public BaseResponseBuilder<T> autoMetadata() {
         ensureMetadata();
         if (this.metadata.getDurationMs() == null) {
            this.metadata.setDurationMs(TraceIdContext.getDurationMs());
         }
         if (this.metadata.getApiVersion() == null) {
            this.metadata.setApiVersion(ResponseMetadataContext.getApiVersion());
         }
         if (this.metadata.getEnvironment() == null) {
            this.metadata.setEnvironment(ResponseMetadataContext.getEnvironment());
         }
         return this;
      }

      private ResponseMetadata ensureMetadata() {
         if (this.metadata == null) {
            this.metadata = new ResponseMetadata();
         }
         return this.metadata;
      }

      public BaseResponseBuilder<T> errors(List<FieldErrorDetail> errors) {
         this.errors = errors;
         return this;
      }

      public BaseResponseBuilder<T> addError(FieldErrorDetail error) {
         if (this.errors == null) {
            this.errors = new ArrayList<>();
         }
         this.errors.add(error);
         return this;
      }

      public BaseResponseBuilder<T> addError(String field, String message) {
         return addError(FieldErrorDetail.of(field, message));
      }

      public BaseResponseBuilder<T> addError(String field, Object rejectedValue, String message) {
         return addError(FieldErrorDetail.of(field, rejectedValue, message));
      }

      public BaseResponseBuilder<T> addError(String field, Object rejectedValue, String message, String code) {
         return addError(FieldErrorDetail.of(field, rejectedValue, message, code));
      }

      public BaseResponseBuilder<T> warnings(List<String> warnings) {
         this.warnings = warnings;
         return this;
      }

      public BaseResponseBuilder<T> addWarning(String warning) {
         if (this.warnings == null) {
            this.warnings = new ArrayList<>();
         }
         this.warnings.add(warning);
         return this;
      }

      public BaseResponseBuilder<T> errorId(String errorId) {
         this.errorId = errorId;
         return this;
      }

      public BaseResponseBuilder<T> traceId(String traceId) {
         this.traceId = traceId;
         return this;
      }

      public BaseResponseBuilder<T> timestamp(Instant timestamp) {
         this.timestamp = timestamp;
         return this;
      }

      public BaseResponseBuilder<T> path(String path) {
         this.path = path;
         return this;
      }

      public BaseResponse<T> build() {
         if (this.level == null) {
            if (Boolean.FALSE.equals(this.success)) {
               this.level = ResponseLevel.ERROR;
            } else {
               this.level = ResponseLevel.SUCCESS;
            }
         }

         boolean resolvedSuccess = (this.success != null) ? this.success : (this.level != ResponseLevel.ERROR);

         String resolvedTraceId = (this.traceId != null) ? this.traceId : TraceIdContext.getTraceId();
         Instant resolvedTimestamp = (this.timestamp != null) ? this.timestamp : Instant.now();

         BaseResponse<T> response = new BaseResponse<>();
         response.setSuccess(resolvedSuccess);
         response.setLevel(this.level);
         response.setCode(this.code);
         response.setMessage(this.message);
         response.setData(this.data);
         response.setPagination(this.pagination);
         response.setMetadata(this.metadata);
         response.setErrors(this.errors);
         response.setWarnings(this.warnings);
         response.setTraceId(resolvedTraceId);
         response.setErrorId(this.errorId);
         response.setTimestamp(resolvedTimestamp);
         response.setPath(this.path);
         return response;
      }
   }
}
