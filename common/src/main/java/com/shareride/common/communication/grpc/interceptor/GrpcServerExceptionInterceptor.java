package com.shareride.common.communication.grpc.interceptor;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.exception.BaseException;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side gRPC interceptor catching unhandled exceptions and standard {@link BaseException}s,
 * properly transforming them into canonical gRPC {@link Status} and trailer metadata.
 */
public class GrpcServerExceptionInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerExceptionInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Throwable t) {
                    handleException(call, t);
                }
            }

            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (Throwable t) {
                    handleException(call, t);
                }
            }
        };
    }

    private <ReqT, RespT> void handleException(ServerCall<ReqT, RespT> call, Throwable throwable) {
        Metadata trailers = new Metadata();
        Status status;

        if (throwable instanceof BaseException baseException) {
            status = GrpcStatusExceptionMapper.toGrpcStatus(baseException);
            trailers.put(GrpcMetadataConstants.ERROR_CODE_KEY, baseException.getCode());
            log.warn(
                    "[gRPC Service Error] {}: {}",
                    baseException.getCode(),
                    baseException.getMessage());
        } else if (throwable instanceof StatusRuntimeException sre) {
            status = sre.getStatus();
            if (sre.getTrailers() != null) {
                trailers.merge(sre.getTrailers());
            }
        } else {
            log.error("[gRPC Unhandled Server Error]: {}", throwable.getMessage(), throwable);
            status =
                    Status.INTERNAL
                            .withDescription("Internal server error: " + throwable.getMessage())
                            .withCause(throwable);
            trailers.put(GrpcMetadataConstants.ERROR_CODE_KEY, "INTERNAL_SERVER_ERROR");
        }

        call.close(status, trailers);
    }
}
