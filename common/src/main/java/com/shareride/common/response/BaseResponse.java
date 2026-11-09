package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.LocaleContextHolderUtil;
import com.shareride.common.i18n.MessageResolverHolder;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enterprise standard API Response wrapper across all ShareRide Microservices. Supports pagination,
 * multi-level responses, nested error reporting, metadata, message localization, and a streamlined
 * fluent builder.
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
    private String correlationId;
    private String errorId;

    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            timezone = "UTC")
    private Instant timestamp;

    private String path;

    public static <T> BaseResponse<T> success(T data) {
        return success(ResponseCode.SUCCESS.getCode(), "Success", data);
    }

    public static <T> BaseResponse<T> success(T data, String message) {
        return success(ResponseCode.SUCCESS.getCode(), message, data);
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
        return success(
                ResponseCode.SUCCESS.getCode(), resolveMessage(messageKey, Map.of(), null), data);
    }

    public static <T> BaseResponse<T> successLocalized(
            String messageKey, Map<String, Object> params, T data) {
        return success(
                ResponseCode.SUCCESS.getCode(), resolveMessage(messageKey, params, null), data);
    }

    public static <T> BaseResponse<T> paginate(T data, PageInfo pagination) {
        return BaseResponse.<T>builder()
                .level(ResponseLevel.SUCCESS)
                .code(ResponseCode.SUCCESS.getCode())
                .message("Success")
                .data(data)
                .pagination(pagination)
                .build();
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
        return paginate(data, validateAndCreatePageInfo(page, size, totalElements));
    }

    public static <T> BaseResponse<T> paginate(
            T data, int page, int size, long totalElements, String message) {
        return BaseResponse.<T>builder()
                .level(ResponseLevel.SUCCESS)
                .code(ResponseCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .pagination(validateAndCreatePageInfo(page, size, totalElements))
                .build();
    }

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
        return BaseResponse.<T>builder()
                .level(ResponseLevel.INFO)
                .fromResponseCode(responseCode)
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

    public static <T> BaseResponse<T> warning(String code, String message, T data) {
        return BaseResponse.<T>builder()
                .level(ResponseLevel.WARNING)
                .code(code)
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
                .code(code)
                .message(message)
                .data(data)
                .build();
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
        return BaseResponse.<T>builder()
                .level(ResponseLevel.WARNING)
                .fromResponseCode(responseCode)
                .data(data)
                .build();
    }

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

    public static <T> BaseResponse<T> error(ResponseCode responseCode) {
        if (responseCode == null) {
            throw new IllegalArgumentException("ResponseCode cannot be null");
        }
        return BaseResponse.<T>builder().fromResponseCode(responseCode).build();
    }

    public static <T> BaseResponse<T> error(
            String code, String message, List<FieldErrorDetail> errors) {
        return BaseResponse.<T>builder()
                .level(ResponseLevel.ERROR)
                .code(code)
                .message(message)
                .errors(errors)
                .build();
    }

    public static <T> BaseResponse<T> error(
            String code, String message, List<FieldErrorDetail> errors, String path) {
        return BaseResponse.<T>builder()
                .level(ResponseLevel.ERROR)
                .code(code)
                .message(message)
                .errors(errors)
                .path(path)
                .build();
    }

    public static <T> BaseResponse<T> errorLocalized(ResponseCode responseCode) {
        return error(responseCode);
    }

    public static <T> BaseResponse<T> errorLocalized(
            ResponseCode responseCode, Map<String, Object> params) {
        if (responseCode == null) {
            throw new IllegalArgumentException("ResponseCode cannot be null");
        }
        return BaseResponse.<T>builder()
                .level(ResponseLevel.ERROR)
                .fromResponseCode(responseCode, params)
                .build();
    }

    public static <T> BaseResponse<T> errorLocalized(
            ResponseCode responseCode, List<FieldErrorDetail> errors) {
        if (responseCode == null) {
            throw new IllegalArgumentException("ResponseCode cannot be null");
        }
        return BaseResponse.<T>builder()
                .level(ResponseLevel.ERROR)
                .fromResponseCode(responseCode)
                .errors(errors)
                .build();
    }

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
        if (errors == null) {
            return 0;
        }
        return errors.stream()
                .mapToInt(e -> 1 + (e.hasNestedErrors() ? e.getNestedErrors().size() : 0))
                .sum();
    }

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
                .correlationId(this.correlationId)
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
        builder.correlationId = this.correlationId;
        builder.errorId = this.errorId;
        builder.timestamp = this.timestamp;
        builder.path = this.path;
        return builder;
    }

    private static String resolveMessage(String key, Map<String, Object> params, Locale locale) {
        Locale targetLocale = (locale != null) ? locale : LocaleContextHolderUtil.getLocale();
        return MessageResolverHolder.getMessage(
                key, params != null ? params : Map.of(), targetLocale);
    }

    private static PageInfo validateAndCreatePageInfo(int page, int size, long totalElements) {
        if (page < 0 || size <= 0 || totalElements < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
        return PageInfo.of(page, size, totalElements);
    }

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
        private String correlationId;
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
            return messageKey(messageKey, Map.of(), null);
        }

        public BaseResponseBuilder<T> messageKey(String messageKey, Map<String, Object> params) {
            return messageKey(messageKey, params, null);
        }

        public BaseResponseBuilder<T> messageKey(
                String messageKey, Map<String, Object> params, Locale locale) {
            this.message = resolveMessage(messageKey, params, locale);
            return this;
        }

        public BaseResponseBuilder<T> fromResponseCode(ResponseCode responseCode) {
            return fromResponseCode(responseCode, Map.of(), null);
        }

        public BaseResponseBuilder<T> fromResponseCode(
                ResponseCode responseCode, Map<String, Object> params) {
            return fromResponseCode(responseCode, params, null);
        }

        public BaseResponseBuilder<T> fromResponseCode(
                ResponseCode responseCode, Map<String, Object> params, Locale locale) {
            if (responseCode != null) {
                this.code = responseCode.getCode();
                this.message = resolveMessage(responseCode.getCode(), params, locale);
            }
            return this;
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
            this.pagination = validateAndCreatePageInfo(page, size, totalElements);
            return this;
        }

        public BaseResponseBuilder<T> metadata(ResponseMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public BaseResponseBuilder<T> metadata(Consumer<ResponseMetadata> consumer) {
            consumer.accept(ensureMetadata());
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
            ResponseMetadata md = ensureMetadata();
            if (md.getDurationMs() == null) {
                md.setDurationMs(TraceIdContext.getDurationMs());
            }
            if (md.getApiVersion() == null) {
                md.setApiVersion(ResponseMetadataContext.getApiVersion());
            }
            if (md.getEnvironment() == null) {
                md.setEnvironment(ResponseMetadataContext.getEnvironment());
            }
            if (ResponseMetadataContext.isDeprecated()) {
                String sunset = ResponseMetadataContext.getSunsetDate();
                String warnMsg =
                        (sunset != null && !sunset.isBlank())
                                ? "API version '"
                                        + this.metadata.getApiVersion()
                                        + "' is deprecated and will sunset on "
                                        + sunset
                                : "API version '"
                                        + this.metadata.getApiVersion()
                                        + "' is deprecated";
                addWarning(warnMsg);
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

        public BaseResponseBuilder<T> correlationId(String correlationId) {
            this.correlationId = correlationId;
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
                this.level =
                        Boolean.FALSE.equals(this.success)
                                ? ResponseLevel.ERROR
                                : ResponseLevel.SUCCESS;
            }
            boolean resolvedSuccess =
                    (this.success != null) ? this.success : (this.level != ResponseLevel.ERROR);
            String resolvedTraceId =
                    (this.traceId != null) ? this.traceId : TraceIdContext.getTraceId();
            String resolvedCorrelationId =
                    (this.correlationId != null)
                            ? this.correlationId
                            : CorrelationIdContext.getCorrelationId();
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
            response.setCorrelationId(resolvedCorrelationId);
            response.setErrorId(this.errorId);
            response.setTimestamp(resolvedTimestamp);
            response.setPath(this.path);
            return response;
        }
    }
}
