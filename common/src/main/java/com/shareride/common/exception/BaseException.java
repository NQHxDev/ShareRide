package com.shareride.common.exception;

import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Root unchecked exception for all business and application errors in ShareRide. */
@Getter
public class BaseException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final Map<String, Object> params;

    public BaseException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
    }

    public BaseException(
            String code, String message, HttpStatus httpStatus, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public BaseException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
    }

    public BaseException(String code, HttpStatus httpStatus) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = Collections.emptyMap();
    }

    public BaseException(String code, HttpStatus httpStatus, Map<String, Object> params) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
        this.params = params != null ? params : Collections.emptyMap();
    }
}
