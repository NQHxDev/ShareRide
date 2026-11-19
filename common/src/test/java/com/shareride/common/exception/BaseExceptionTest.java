package com.shareride.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.response.FieldErrorDetail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("BaseException Unit Tests")
class BaseExceptionTest {

    @Test
    @DisplayName("Should verify constructor with code, message, status, and cause")
    void testConstructorWithCause() {
        Throwable cause = new IllegalArgumentException("Root cause");
        BaseException ex =
                new BaseException("ERR_001", "Custom error", HttpStatus.BAD_REQUEST, cause);

        assertThat(ex.getCode()).isEqualTo("ERR_001");
        assertThat(ex.getMessage()).isEqualTo("Custom error");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getParams()).isEmpty();
        assertThat(ex.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Should verify constructor with code, message, status, errors, and cause")
    void testConstructorWithErrorsAndCause() {
        Throwable cause = new IllegalStateException("Deep error");
        FieldErrorDetail fieldError =
                FieldErrorDetail.builder().field("username").message("cannot be blank").build();
        BaseException ex =
                new BaseException(
                        "ERR_002",
                        "Validation failed",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of(fieldError),
                        cause);

        assertThat(ex.getCode()).isEqualTo("ERR_002");
        assertThat(ex.getMessage()).isEqualTo("Validation failed");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getErrors()).containsExactly(fieldError);
    }

    @Test
    @DisplayName("Should verify all standard constructor overloads")
    void testStandardConstructors() {
        BaseException ex1 = new BaseException("CODE_1", HttpStatus.NOT_FOUND);
        assertThat(ex1.getCode()).isEqualTo("CODE_1");
        assertThat(ex1.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        BaseException ex2 = new BaseException("CODE_2", HttpStatus.FORBIDDEN, Map.of("key", "val"));
        assertThat(ex2.getParams()).containsEntry("key", "val");

        BaseException ex3 =
                new BaseException(
                        "CODE_3",
                        "Message 3",
                        HttpStatus.BAD_REQUEST,
                        List.of(FieldErrorDetail.builder().field("f").message("msg").build()));
        assertThat(ex3.getErrors()).hasSize(1);
    }
}
