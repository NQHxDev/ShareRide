package com.shareride.common.exception;

import com.shareride.common.constant.ResponseCode;

import java.util.Map;

/**
 * 429 Too Many Requests / Rate Limit Exceeded exception.
 */
public class RateLimitExceededException extends BaseException {

   public RateLimitExceededException() {
      super(ResponseCode.RATE_LIMIT_EXCEEDED.getCode(), ResponseCode.RATE_LIMIT_EXCEEDED.getHttpStatus());
   }

   public RateLimitExceededException(String message) {
      super(ResponseCode.RATE_LIMIT_EXCEEDED.getCode(), message, ResponseCode.RATE_LIMIT_EXCEEDED.getHttpStatus());
   }

   public RateLimitExceededException(Map<String, Object> params) {
      super(ResponseCode.RATE_LIMIT_EXCEEDED.getCode(), ResponseCode.RATE_LIMIT_EXCEEDED.getHttpStatus(), params);
   }

   public RateLimitExceededException(String message, Map<String, Object> params) {
      super(ResponseCode.RATE_LIMIT_EXCEEDED.getCode(), message, ResponseCode.RATE_LIMIT_EXCEEDED.getHttpStatus(),
            params);
   }

}
