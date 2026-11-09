package com.shareride.common.versioning;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.BaseException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Thrown when an API version is required by configuration but omitted by the client. */
public class ApiVersionRequiredException extends BaseException {

    public ApiVersionRequiredException() {
        super(
                ResponseCode.UNSUPPORTED_API_VERSION.getCode(),
                "API version is required but was not provided",
                HttpStatus.BAD_REQUEST,
                Map.of("required", "true"));
    }
}
