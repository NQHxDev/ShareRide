package com.shareride.common.exception;

import com.shareride.common.response.FieldErrorDetail;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Root unchecked exception for all business and application errors in ShareRide. */
@Getter
public class BaseException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final Map<String, Object> params;
    private final List<FieldErrorDetail> errors;

    public BaseException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
        this.errors = Collections.emptyList();
    }

    public BaseException(
            String code, String message, HttpStatus httpStatus, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = params != null ? params : Collections.emptyMap();
        this.errors = Collections.emptyList();
    }

    public BaseException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
        this.errors = Collections.emptyList();
    }

    public BaseException(String code, HttpStatus httpStatus) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
        this.errors = Collections.emptyList();
    }

    public BaseException(String code, HttpStatus httpStatus, Map<String, Object> params) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = params != null ? params : Collections.emptyMap();
        this.errors = Collections.emptyList();
    }

    public BaseException(
            String code, String message, HttpStatus httpStatus, List<FieldErrorDetail> errors) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
        this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
    }

    public BaseException(
            String code,
            String message,
            HttpStatus httpStatus,
            List<FieldErrorDetail> errors,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
        this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
    }
}
