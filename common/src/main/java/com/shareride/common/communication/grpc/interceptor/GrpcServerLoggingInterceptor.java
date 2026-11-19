package com.shareride.common.communication.grpc.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC server interceptor that logs structured information about incoming RPC calls including
 * method name, status codes, and latency measurements for observability and debugging.
 */
@Slf4j
public class GrpcServerLoggingInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        long startTimeNanos = System.nanoTime();
        String methodName = call.getMethodDescriptor().getFullMethodName();

        log.debug("gRPC server call received: method={}", methodName);

        ServerCall<ReqT, RespT> monitoringCall =
                new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {

                    @Override
                    public void close(Status status, Metadata trailers) {
                        long latencyMs = (System.nanoTime() - startTimeNanos) / 1_000_000;

                        if (status.isOk()) {
                            log.debug(
                                    "gRPC server call completed: method={}, status={},"
                                            + " latencyMs={}",
                                    methodName,
                                    status.getCode(),
                                    latencyMs);
                        } else {
                            log.warn(
                                    "gRPC server call failed: method={}, status={},"
                                            + " description={}, latencyMs={}",
                                    methodName,
                                    status.getCode(),
                                    status.getDescription(),
                                    latencyMs);
                        }

                        super.close(status, trailers);
                    }
                };

        ServerCall.Listener<ReqT> listener = next.startCall(monitoringCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {

            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } catch (Exception e) {
                    log.error("Error during gRPC call completion: method={}", methodName, e);
                    throw e;
                }
            }

            @Override
            public void onCancel() {
                log.debug("gRPC server call cancelled: method={}", methodName);
                super.onCancel();
            }
        };
    }
}
