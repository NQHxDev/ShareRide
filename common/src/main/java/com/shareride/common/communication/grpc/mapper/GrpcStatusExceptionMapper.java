package com.shareride.common.communication.grpc.mapper;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.BadRequest;
import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.BaseException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.RequestTimeoutException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.response.FieldErrorDetail;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Bi-directional translator between gRPC Status codes and ShareRide standard {@link BaseException}
 * hierarchy. Ensures seamless error bridging between internal gRPC microservices and API Gateway /
 * REST responses.
 *
 * <p>Supports Google RPC Rich Error Model (com.google.rpc.Status) for detailed field-level
 * validation errors.
 */
public final class GrpcStatusExceptionMapper {

    private GrpcStatusExceptionMapper() {}

    /** Maps a {@link BaseException} to a gRPC {@link Status}. */
    public static Status toGrpcStatus(BaseException exception) {
        if (exception == null) {
            return Status.INTERNAL;
        }

        String description =
                exception.getMessage() != null ? exception.getMessage() : exception.getCode();

        if (exception instanceof BadRequestException) {
            return Status.INVALID_ARGUMENT.withDescription(description).withCause(exception);
        } else if (exception instanceof UnauthorizedException) {
            return Status.UNAUTHENTICATED.withDescription(description).withCause(exception);
        } else if (exception instanceof ForbiddenException) {
            return Status.PERMISSION_DENIED.withDescription(description).withCause(exception);
        } else if (exception instanceof ResourceNotFoundException) {
            return Status.NOT_FOUND.withDescription(description).withCause(exception);
        } else if (exception instanceof ConflictException) {
            return Status.ALREADY_EXISTS.withDescription(description).withCause(exception);
        } else if (exception instanceof RequestTimeoutException) {
            return Status.DEADLINE_EXCEEDED.withDescription(description).withCause(exception);
        } else if (exception instanceof RateLimitExceededException) {
            return Status.RESOURCE_EXHAUSTED.withDescription(description).withCause(exception);
        }

        // Fallback by HTTP Status
        HttpStatus httpStatus = exception.getHttpStatus();
        if (httpStatus != null) {
            return switch (httpStatus.series()) {
                case CLIENT_ERROR ->
                        Status.INVALID_ARGUMENT.withDescription(description).withCause(exception);
                case SERVER_ERROR ->
                        Status.INTERNAL.withDescription(description).withCause(exception);
                default -> Status.UNKNOWN.withDescription(description).withCause(exception);
            };
        }

        return Status.INTERNAL.withDescription(description).withCause(exception);
    }

    /**
     * Translates a gRPC {@link StatusRuntimeException} into a specific {@link BaseException}. When
     * Gateway calls a downstream gRPC service and gets an error, this restores the native Java
     * exception.
     *
     * <p>Supports Google RPC Rich Error Model: extracts {@link BadRequest} field violations from
     * {@link com.google.rpc.Status} details and converts them to {@link FieldErrorDetail} list.
     */
    public static BaseException toBaseException(StatusRuntimeException exception) {
        if (exception == null) {
            return new BaseException(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCode(),
                    "Unknown gRPC error",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Status status = exception.getStatus();
        Metadata trailers = exception.getTrailers();
        String errorCode = null;
        if (trailers != null) {
            errorCode = trailers.get(GrpcMetadataConstants.ERROR_CODE_KEY);
        }

        String message =
                status.getDescription() != null ? status.getDescription() : status.getCode().name();

        // Try to extract Google RPC Rich Error Model details
        List<FieldErrorDetail> fieldErrors = extractFieldErrors(exception);

        // For INVALID_ARGUMENT with field errors, create BadRequestException with field details
        if (status.getCode() == Status.Code.INVALID_ARGUMENT && !fieldErrors.isEmpty()) {
            String errorMessage =
                    message != null && !message.isEmpty() ? message : "Validation failed";
            return new BadRequestException(
                    errorCode != null ? errorCode : ResponseCode.VALIDATION_FAILED.getCode(),
                    errorMessage,
                    fieldErrors);
        }

        return switch (status.getCode()) {
            case INVALID_ARGUMENT ->
                    new BadRequestException(
                            errorCode != null ? errorCode : ResponseCode.BAD_REQUEST.getCode(),
                            message);
            case UNAUTHENTICATED -> new UnauthorizedException(message);
            case PERMISSION_DENIED -> new ForbiddenException(message);
            case NOT_FOUND -> new ResourceNotFoundException(message);
            case ALREADY_EXISTS -> new ConflictException(message);
            case DEADLINE_EXCEEDED -> new RequestTimeoutException(message);
            case RESOURCE_EXHAUSTED -> new RateLimitExceededException(message);
            case UNAVAILABLE ->
                    new BaseException(
                            errorCode != null
                                    ? errorCode
                                    : ResponseCode.SERVICE_UNAVAILABLE.getCode(),
                            "Downstream service unavailable: " + message,
                            HttpStatus.SERVICE_UNAVAILABLE);
            default ->
                    new BaseException(
                            errorCode != null
                                    ? errorCode
                                    : ResponseCode.INTERNAL_SERVER_ERROR.getCode(),
                            message,
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            exception);
        };
    }

    /**
     * Extracts field-level validation errors from Google RPC Rich Error Model ({@link
     * com.google.rpc.Status}). Public API for use in test assertions and custom exception mapping.
     *
     * @param exception the gRPC StatusRuntimeException
     * @return list of FieldErrorDetail, or empty list if no rich error details found
     */
    public static List<FieldErrorDetail> extractFieldErrors(StatusRuntimeException exception) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();

        try {
            // Extract com.google.rpc.Status from the exception
            com.google.rpc.Status rpcStatus = StatusProto.fromThrowable(exception);
            if (rpcStatus == null) {
                return fieldErrors;
            }

            // Iterate through all details in the status
            for (Any detail : rpcStatus.getDetailsList()) {
                // Check if this detail is a BadRequest message
                if (detail.is(BadRequest.class)) {
                    try {
                        BadRequest badRequest = detail.unpack(BadRequest.class);

                        // Extract field violations and convert to FieldErrorDetail
                        for (BadRequest.FieldViolation violation :
                                badRequest.getFieldViolationsList()) {
                            FieldErrorDetail fieldError =
                                    FieldErrorDetail.builder()
                                            .field(violation.getField())
                                            .message(violation.getDescription())
                                            .build();
                            fieldErrors.add(fieldError);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        // If unpacking fails, skip this detail
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            // If any error occurs during extraction, return empty list and fall back to standard
            // error handling
            return new ArrayList<>();
        }

        return fieldErrors;
    }
}
