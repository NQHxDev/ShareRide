package com.shareride.common.exception;

import com.shareride.common.constant.ResponseCode;
import java.util.Map;

/** 408 Request Timeout exception. */
public class RequestTimeoutException extends BaseException {

    public RequestTimeoutException() {
        super(ResponseCode.REQUEST_TIMEOUT.getCode(), ResponseCode.REQUEST_TIMEOUT.getHttpStatus());
    }

    public RequestTimeoutException(String message) {
        super(
                ResponseCode.REQUEST_TIMEOUT.getCode(),
                message,
                ResponseCode.REQUEST_TIMEOUT.getHttpStatus());
    }

    public RequestTimeoutException(Map<String, Object> params) {
        super(
                ResponseCode.REQUEST_TIMEOUT.getCode(),
                ResponseCode.REQUEST_TIMEOUT.getHttpStatus(),
                params);
    }

    public RequestTimeoutException(String message, Map<String, Object> params) {
        super(
                ResponseCode.REQUEST_TIMEOUT.getCode(),
                message,
                ResponseCode.REQUEST_TIMEOUT.getHttpStatus(),
                params);
    }
}
