package com.shareride.common.communication.grpc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.rpc.BadRequest;
import com.google.rpc.BadRequest.FieldViolation;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.BaseException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.RequestTimeoutException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcStatusExceptionMapperTest {

    @Test
    void testToGrpcStatusFromBadRequestException() {
        BadRequestException exception = new BadRequestException("Invalid input");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).isEqualTo("Invalid input");
    }

    @Test
    void testToGrpcStatusFromUnauthorizedException() {
        UnauthorizedException exception = new UnauthorizedException("Invalid credentials");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(status.getDescription()).isEqualTo("Invalid credentials");
    }

    @Test
    void testToGrpcStatusFromForbiddenException() {
        ForbiddenException exception = new ForbiddenException("Access denied");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getDescription()).isEqualTo("Access denied");
    }

    @Test
    void testToGrpcStatusFromResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("User not found");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(status.getDescription()).isEqualTo("User not found");
    }

    @Test
    void testToGrpcStatusFromConflictException() {
        ConflictException exception = new ConflictException("Email already exists");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(status.getDescription()).isEqualTo("Email already exists");
    }

    @Test
    void testToGrpcStatusFromRequestTimeoutException() {
        RequestTimeoutException exception = new RequestTimeoutException("Request timed out");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(status.getDescription()).isEqualTo("Request timed out");
    }

    @Test
    void testToGrpcStatusFromRateLimitExceededException() {
        RateLimitExceededException exception =
                new RateLimitExceededException("Rate limit exceeded");

        Status status = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        assertThat(status.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(status.getDescription()).isEqualTo("Rate limit exceeded");
    }

    @Test
    void testToBaseExceptionFromInvalidArgument() {
        StatusRuntimeException grpcException =
                Status.INVALID_ARGUMENT.withDescription("Bad request").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(BadRequestException.class);
        assertThat(result.getMessage()).isEqualTo("Bad request");
    }

    @Test
    void testToBaseExceptionFromUnauthenticated() {
        StatusRuntimeException grpcException =
                Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(UnauthorizedException.class);
        assertThat(result.getMessage()).isEqualTo("Not authenticated");
    }

    @Test
    void testToBaseExceptionFromPermissionDenied() {
        StatusRuntimeException grpcException =
                Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(ForbiddenException.class);
        assertThat(result.getMessage()).isEqualTo("Forbidden");
    }

    @Test
    void testToBaseExceptionFromNotFound() {
        StatusRuntimeException grpcException =
                Status.NOT_FOUND.withDescription("Resource not found").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(ResourceNotFoundException.class);
        assertThat(result.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void testToBaseExceptionFromAlreadyExists() {
        StatusRuntimeException grpcException =
                Status.ALREADY_EXISTS.withDescription("Conflict").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(ConflictException.class);
        assertThat(result.getMessage()).isEqualTo("Conflict");
    }

    @Test
    void testToBaseExceptionFromDeadlineExceeded() {
        StatusRuntimeException grpcException =
                Status.DEADLINE_EXCEEDED.withDescription("Timeout").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(RequestTimeoutException.class);
        assertThat(result.getMessage()).isEqualTo("Timeout");
    }

    @Test
    void testToBaseExceptionFromResourceExhausted() {
        StatusRuntimeException grpcException =
                Status.RESOURCE_EXHAUSTED.withDescription("Too many requests").asRuntimeException();

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(RateLimitExceededException.class);
        assertThat(result.getMessage()).isEqualTo("Too many requests");
    }

    @Test
    void testExtractFieldViolationsFromRichErrorModel() {
        FieldViolation emailViolation =
                FieldViolation.newBuilder()
                        .setField("email")
                        .setDescription("Email must be a valid email address")
                        .build();

        FieldViolation passwordViolation =
                FieldViolation.newBuilder()
                        .setField("password")
                        .setDescription("Password must be at least 8 characters")
                        .build();

        BadRequest badRequest =
                BadRequest.newBuilder()
                        .addFieldViolations(emailViolation)
                        .addFieldViolations(passwordViolation)
                        .build();

        com.google.rpc.Status status =
                com.google.rpc.Status.newBuilder()
                        .setCode(io.grpc.Status.Code.INVALID_ARGUMENT.value())
                        .setMessage("Validation failed")
                        .addDetails(Any.pack(badRequest))
                        .build();

        StatusRuntimeException grpcException = StatusProto.toStatusRuntimeException(status);

        BaseException result = GrpcStatusExceptionMapper.toBaseException(grpcException);

        assertThat(result).isInstanceOf(BadRequestException.class);
        assertThat(result.getMessage()).contains("Validation failed");
    }

    @Test
    void testToBaseExceptionWithNullInput() {
        BaseException result = GrpcStatusExceptionMapper.toBaseException(null);

        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("Unknown gRPC error");
    }

    @Test
    void testToGrpcStatusWithNullInput() {
        Status status = GrpcStatusExceptionMapper.toGrpcStatus(null);

        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
    }
}
