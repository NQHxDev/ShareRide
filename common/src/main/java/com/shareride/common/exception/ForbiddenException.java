package com.shareride.common.exception;

import org.springframework.http.HttpStatus;

/** 403 Forbidden exception. */
public class ForbiddenException extends BaseException {

    public ForbiddenException() {
        super("FORBIDDEN", HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
