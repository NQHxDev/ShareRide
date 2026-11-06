package com.shareride.common.communication.grpc.mapper;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.*;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;

/**
 * Bi-directional translator between gRPC Status codes and ShareRide standard {@link BaseException} hierarchy.
 * Ensures seamless error bridging between internal gRPC microservices and API Gateway / REST responses.
 */
public final class GrpcStatusExceptionMapper {

    private GrpcStatusExceptionMapper() {
    }

    /**
     * Maps a {@link BaseException} to a gRPC {@link Status}.
     */
    public static Status toGrpcStatus(BaseException exception) {
        if (exception == null) {
            return Status.INTERNAL;
        }

        String description = exception.getMessage() != null ? exception.getMessage() : exception.getCode();

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
                case CLIENT_ERROR -> Status.INVALID_ARGUMENT.withDescription(description).withCause(exception);
                case SERVER_ERROR -> Status.INTERNAL.withDescription(description).withCause(exception);
                default -> Status.UNKNOWN.withDescription(description).withCause(exception);
            };
        }

        return Status.INTERNAL.withDescription(description).withCause(exception);
    }

    /**
     * Translates a gRPC {@link StatusRuntimeException} into a specific {@link BaseException}.
     * When Gateway calls a downstream gRPC service and gets an error, this restores the native Java exception.
     */
    public static BaseException toBaseException(StatusRuntimeException exception) {
        if (exception == null) {
            return new BaseException(ResponseCode.INTERNAL_SERVER_ERROR.getCode(),
                    "Unknown gRPC error", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Status status = exception.getStatus();
        Metadata trailers = exception.getTrailers();
        String errorCode = null;
        if (trailers != null) {
            errorCode = trailers.get(GrpcMetadataConstants.ERROR_CODE_KEY);
        }

        String message = status.getDescription() != null ? status.getDescription() : status.getCode().name();

        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> new BadRequestException(message);
            case UNAUTHENTICATED -> new UnauthorizedException(message);
            case PERMISSION_DENIED -> new ForbiddenException(message);
            case NOT_FOUND -> new ResourceNotFoundException(message);
            case ALREADY_EXISTS -> new ConflictException(message);
            case DEADLINE_EXCEEDED -> new RequestTimeoutException(message);
            case RESOURCE_EXHAUSTED -> new RateLimitExceededException(message);
            case UNAVAILABLE -> new BaseException(
                    errorCode != null ? errorCode : ResponseCode.SERVICE_UNAVAILABLE.getCode(),
                    "Downstream service unavailable: " + message,
                    HttpStatus.SERVICE_UNAVAILABLE
            );
            default -> new BaseException(
                    errorCode != null ? errorCode : ResponseCode.INTERNAL_SERVER_ERROR.getCode(),
                    message,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception
            );
        };
    }
}
