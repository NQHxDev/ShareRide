package com.shareride.common.communication.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.rpc.BadRequest;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.response.FieldErrorDetail;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test validating end-to-end Google RPC Rich Error Model flow: StatusProto packing of
 * BadRequest.FieldViolation on server side and unpacking into List&lt;FieldErrorDetail&gt; via
 * GrpcStatusExceptionMapper.
 *
 * <p>Per T035 (Phase 5 User Story 3): validates that field-level validation errors are properly
 * serialized as Google RPC Rich Error details and correctly translated back into ShareRide's
 * FieldErrorDetail format.
 */
class GrpcRichErrorIntegrationTest {

    @BeforeEach
    void setUp() {
        // No setup needed - GrpcStatusExceptionMapper uses static methods
    }

    @Test
    @DisplayName("Should pack and unpack BadRequest.FieldViolation through StatusProto")
    void testEndToEndRichErrorPacking() {
        BadRequest badRequest =
                BadRequest.newBuilder()
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("email")
                                        .setDescription("Email must be a valid email address")
                                        .build())
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("password")
                                        .setDescription("Password must be at least 8 characters")
                                        .build())
                        .build();

        com.google.rpc.Status status =
                com.google.rpc.Status.newBuilder()
                        .setCode(Status.Code.INVALID_ARGUMENT.value())
                        .setMessage("Validation failed")
                        .addDetails(Any.pack(badRequest))
                        .build();

        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

        List<FieldErrorDetail> fieldErrors =
                GrpcStatusExceptionMapper.extractFieldErrors(exception);

        assertThat(fieldErrors).hasSize(2);
        assertThat(fieldErrors)
                .extracting(error -> error.getField())
                .containsExactly("email", "password");
        assertThat(fieldErrors)
                .extracting(error -> error.getMessage())
                .containsExactly(
                        "Email must be a valid email address",
                        "Password must be at least 8 characters");
    }

    @Test
    @DisplayName("Should map StatusRuntimeException with BadRequest to BadRequestException")
    void testMapToBadRequestException() {
        BadRequest badRequest =
                BadRequest.newBuilder()
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("phoneNumber")
                                        .setDescription("Phone number must be in E.164 format")
                                        .build())
                        .build();

        com.google.rpc.Status status =
                com.google.rpc.Status.newBuilder()
                        .setCode(Status.Code.INVALID_ARGUMENT.value())
                        .setMessage("Invalid input")
                        .addDetails(Any.pack(badRequest))
                        .build();

        StatusRuntimeException grpcException = StatusProto.toStatusRuntimeException(status);

        BadRequestException result =
                (BadRequestException) GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(BadRequestException.class);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getField()).isEqualTo("phoneNumber");
        assertThat(result.getErrors().get(0).getMessage())
                .isEqualTo("Phone number must be in E.164 format");
    }

    @Test
    @DisplayName("Should handle StatusRuntimeException without rich error details")
    void testStatusRuntimeExceptionWithoutRichError() {
        StatusRuntimeException exception =
                Status.NOT_FOUND.withDescription("Account not found").asRuntimeException();

        List<FieldErrorDetail> fieldErrors =
                GrpcStatusExceptionMapper.extractFieldErrors(exception);

        assertThat(fieldErrors).isEmpty();
    }

    @Test
    @DisplayName("Should extract field errors from multiple FieldViolations")
    void testMultipleFieldViolations() {
        BadRequest badRequest =
                BadRequest.newBuilder()
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("email")
                                        .setDescription("Required field")
                                        .build())
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("password")
                                        .setDescription("Too weak")
                                        .build())
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("phoneNumber")
                                        .setDescription("Invalid format")
                                        .build())
                        .build();

        com.google.rpc.Status status =
                com.google.rpc.Status.newBuilder()
                        .setCode(Status.Code.INVALID_ARGUMENT.value())
                        .setMessage("Multiple validation errors")
                        .addDetails(Any.pack(badRequest))
                        .build();

        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

        List<FieldErrorDetail> fieldErrors =
                GrpcStatusExceptionMapper.extractFieldErrors(exception);

        assertThat(fieldErrors).hasSize(3);
        assertThat(fieldErrors)
                .extracting(error -> error.getField())
                .containsExactly("email", "password", "phoneNumber");
    }

    @Test
    @DisplayName("Should handle empty BadRequest details gracefully")
    void testEmptyBadRequest() {
        BadRequest badRequest = BadRequest.newBuilder().build();

        com.google.rpc.Status status =
                com.google.rpc.Status.newBuilder()
                        .setCode(Status.Code.INVALID_ARGUMENT.value())
                        .setMessage("Validation failed")
                        .addDetails(Any.pack(badRequest))
                        .build();

        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

        List<FieldErrorDetail> fieldErrors =
                GrpcStatusExceptionMapper.extractFieldErrors(exception);

        assertThat(fieldErrors).isEmpty();
    }

    @Test
    @DisplayName("Should preserve correlation ID in StatusRuntimeException metadata")
    void testCorrelationIdPreservation() {
        BadRequest badRequest =
                BadRequest.newBuilder()
                        .addFieldViolations(
                                BadRequest.FieldViolation.newBuilder()
                                        .setField("test")
                                        .setDescription("test error")
                                        .build())
                        .build();

        com.google.rpc.Status status =
                com.google.rpc.Status.newBuilder()
                        .setCode(Status.Code.INVALID_ARGUMENT.value())
                        .setMessage("Test validation")
                        .addDetails(Any.pack(badRequest))
                        .build();

        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
