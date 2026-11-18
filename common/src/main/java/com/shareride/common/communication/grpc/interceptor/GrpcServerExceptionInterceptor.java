package com.shareride.common.communication.grpc.interceptor;

import com.google.protobuf.Any;
import com.google.rpc.BadRequest;
import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.BaseException;
import com.shareride.common.response.FieldErrorDetail;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC server interceptor that catches exceptions on the server side and translates them into
 * appropriate gRPC Status responses.
 *
 * <p>Preserves {@link StatusRuntimeException} and {@link StatusException} with their rich error
 * trailers, maps {@link BaseException} via {@link GrpcStatusExceptionMapper}, and sanitizes
 * unexpected runtime exceptions to Status.INTERNAL to prevent raw stack trace leakage.
 *
 * <p>Enriches all error responses with correlation ID and error code in trailers for debugging.
 *
 * <p>Supports Google RPC Rich Error Model: translates {@link BadRequestException} with field errors
 * into {@link com.google.rpc.Status} with {@link BadRequest} details.
 */
@Slf4j
public class GrpcServerExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        String correlationId = headers.get(GrpcMetadataConstants.CORRELATION_ID_KEY);

        // Generate correlation ID if missing for better traceability
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug(
                    "Generated correlation ID for gRPC call: method={}, correlationId={}",
                    methodName,
                    correlationId);
        }

        final String finalCorrelationId = correlationId;

        ServerCall<ReqT, RespT> wrappedCall =
                new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        // Always enrich with correlation ID
                        if (finalCorrelationId != null && !finalCorrelationId.isEmpty()) {
                            trailers.put(
                                    GrpcMetadataConstants.CORRELATION_ID_KEY, finalCorrelationId);
                        }
                        super.close(status, trailers);
                    }
                };

        try {
            ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);

            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                    listener) {

                @Override
                public void onHalfClose() {
                    try {
                        super.onHalfClose();
                    } catch (Exception e) {
                        handleException(e, wrappedCall, methodName, finalCorrelationId);
                    }
                }

                @Override
                public void onCancel() {
                    try {
                        super.onCancel();
                    } catch (Exception e) {
                        log.error(
                                "Exception during gRPC call cancellation: method={},"
                                        + " correlationId={}",
                                methodName,
                                finalCorrelationId,
                                e);
                    }
                }

                @Override
                public void onComplete() {
                    try {
                        super.onComplete();
                    } catch (Exception e) {
                        handleException(e, wrappedCall, methodName, finalCorrelationId);
                    }
                }

                @Override
                public void onReady() {
                    try {
                        super.onReady();
                    } catch (Exception e) {
                        handleException(e, wrappedCall, methodName, finalCorrelationId);
                    }
                }
            };

        } catch (Exception e) {
            // Handle exception during startCall - close the call but don't re-throw
            // as we've already sent the error status to the client
            handleException(e, wrappedCall, methodName, finalCorrelationId);
            // Return a no-op listener since we've already closed the call
            return new ServerCall.Listener<ReqT>() {};
        }
    }

    private <ReqT, RespT> void handleException(
            Exception e, ServerCall<ReqT, RespT> call, String methodName, String correlationId) {

        Status status;
        Metadata trailers = new Metadata();

        // Preserve StatusRuntimeException with its trailers (includes rich error details)
        if (e instanceof StatusRuntimeException statusRuntimeException) {
            status = statusRuntimeException.getStatus();
            Metadata existingTrailers = statusRuntimeException.getTrailers();
            if (existingTrailers != null) {
                trailers.merge(existingTrailers);
            }
            log.warn(
                    "gRPC StatusRuntimeException in server call: method={}, status={},"
                            + " correlationId={}",
                    methodName,
                    status.getCode(),
                    correlationId);
        } else if (e instanceof StatusException statusException) {
            // Preserve StatusException with its trailers
            status = statusException.getStatus();
            Metadata existingTrailers = statusException.getTrailers();
            if (existingTrailers != null) {
                trailers.merge(existingTrailers);
            }
            log.warn(
                    "gRPC StatusException in server call: method={}, status={}, correlationId={}",
                    methodName,
                    status.getCode(),
                    correlationId);
        } else if (e instanceof BaseException baseException) {
            // Map BaseException to appropriate gRPC Status with rich error details
            status = mapBaseExceptionToStatus(baseException);

            // Propagate error code in trailers for debugging and client-side mapping
            if (baseException.getCode() != null && !baseException.getCode().isEmpty()) {
                trailers.put(GrpcMetadataConstants.ERROR_CODE_KEY, baseException.getCode());
            }

            log.warn(
                    "Business exception in gRPC server call: method={}, code={}, correlationId={}",
                    methodName,
                    baseException.getCode(),
                    correlationId);
        } else {
            // Sanitize unexpected runtime exceptions to prevent stack trace leakage
            log.error(
                    "Unhandled exception in gRPC server call: method={}, correlationId={}",
                    methodName,
                    correlationId,
                    e);

            String sanitizedMessage = "Internal server error";
            if (correlationId != null && !correlationId.isEmpty()) {
                sanitizedMessage += " (correlationId: " + correlationId + ")";
            }
            status = Status.INTERNAL.withDescription(sanitizedMessage);
        }

        // Enrich trailers with correlation ID
        if (correlationId != null && !correlationId.isEmpty()) {
            trailers.put(GrpcMetadataConstants.CORRELATION_ID_KEY, correlationId);
        }

        try {
            call.close(status, trailers);
        } catch (Exception closeException) {
            log.error(
                    "Failed to close call after exception: method={}, correlationId={}",
                    methodName,
                    correlationId,
                    closeException);
        }
    }

    /**
     * Maps a BaseException to gRPC Status with Google RPC Rich Error Model support.
     *
     * <p>For {@link BadRequestException} with field errors, constructs a {@link
     * com.google.rpc.Status} with {@link BadRequest} details containing field violations.
     *
     * @param exception the BaseException to map
     * @return Status object with optional rich error details
     */
    private Status mapBaseExceptionToStatus(BaseException exception) {
        Status baseStatus = GrpcStatusExceptionMapper.toGrpcStatus(exception);

        // Check if this is a BadRequestException with field errors
        if (exception instanceof BadRequestException
                && exception.getErrors() != null
                && !exception.getErrors().isEmpty()) {

            try {
                // Build Google RPC BadRequest with field violations
                BadRequest.Builder badRequestBuilder = BadRequest.newBuilder();
                for (FieldErrorDetail fieldError : exception.getErrors()) {
                    BadRequest.FieldViolation violation =
                            BadRequest.FieldViolation.newBuilder()
                                    .setField(
                                            fieldError.getField() != null
                                                    ? fieldError.getField()
                                                    : "")
                                    .setDescription(
                                            fieldError.getMessage() != null
                                                    ? fieldError.getMessage()
                                                    : "")
                                    .build();
                    badRequestBuilder.addFieldViolations(violation);
                }

                // Wrap in com.google.rpc.Status
                com.google.rpc.Status rpcStatus =
                        com.google.rpc.Status.newBuilder()
                                .setCode(baseStatus.getCode().value())
                                .setMessage(
                                        baseStatus.getDescription() != null
                                                ? baseStatus.getDescription()
                                                : "")
                                .addDetails(Any.pack(badRequestBuilder.build()))
                                .build();

                // Convert to StatusRuntimeException and extract Status
                StatusRuntimeException richException =
                        StatusProto.toStatusRuntimeException(rpcStatus);
                return richException.getStatus();

            } catch (Exception e) {
                // If rich error model construction fails, fall back to basic status
                log.warn(
                        "Failed to construct rich error model for BadRequestException, falling"
                                + " back to basic status",
                        e);
                return baseStatus;
            }
        }

        return baseStatus;
    }
}
