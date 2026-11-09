package com.shareride.common.exception;

import org.springframework.http.HttpStatus;

/** 401 Unauthorized exception. */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException() {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
}
