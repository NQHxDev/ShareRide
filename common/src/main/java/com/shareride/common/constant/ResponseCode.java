package com.shareride.common.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Common response / business error codes.
 */
@Getter
public enum ResponseCode {

   SUCCESS("SUCCESS", HttpStatus.OK),
   CREATED("CREATED", HttpStatus.CREATED),
   BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST),
   UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
   FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),
   NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),
   METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED),
   CONFLICT("CONFLICT", HttpStatus.CONFLICT),
   VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST),
   INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
   SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

   private final String code;
   private final HttpStatus httpStatus;

   ResponseCode(String code, HttpStatus httpStatus) {
      this.code = code;
      this.httpStatus = httpStatus;
   }

}
