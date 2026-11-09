package com.shareride.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 409 Conflict exception. */
public class ConflictException extends BaseException {

    public ConflictException(String code) {
        super(code, HttpStatus.CONFLICT);
    }

    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }

    public ConflictException(String code, Map<String, Object> params) {
        super(code, HttpStatus.CONFLICT, params);
    }
}
