package com.shareride.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 404 Not Found business exception. */
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                Map.of("resource", resource, "identifier", String.valueOf(identifier)));
    }

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
