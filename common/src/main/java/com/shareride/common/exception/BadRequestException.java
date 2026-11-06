package com.shareride.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 400 Bad Request exception.
 */
public class BadRequestException extends BaseException {

   public BadRequestException(String code) {
      super(code, HttpStatus.BAD_REQUEST);
   }

   public BadRequestException(String code, String message) {
      super(code, message, HttpStatus.BAD_REQUEST);
   }

   public BadRequestException(String code, Map<String, Object> params) {
      super(code, HttpStatus.BAD_REQUEST, params);
   }

}
