package com.shareride.common.communication.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC client interceptor that logs structured information about outgoing RPC calls including
 * method name, status codes, and latency measurements for observability and debugging.
 */
@Slf4j
public class GrpcClientLoggingInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            private long startTimeNanos;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                startTimeNanos = System.nanoTime();

                log.debug(
                        "gRPC client call started: method={}, deadline={}",
                        method.getFullMethodName(),
                        callOptions.getDeadline());

                super.start(
                        new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                                responseListener) {

                            @Override
                            public void onClose(Status status, Metadata trailers) {
                                long latencyMs = (System.nanoTime() - startTimeNanos) / 1_000_000;

                                if (status.isOk()) {
                                    log.debug(
                                            "gRPC client call completed: method={}, status={},"
                                                    + " latencyMs={}",
                                            method.getFullMethodName(),
                                            status.getCode(),
                                            latencyMs);
                                } else {
                                    log.warn(
                                            "gRPC client call failed: method={}, status={},"
                                                    + " description={}, latencyMs={}",
                                            method.getFullMethodName(),
                                            status.getCode(),
                                            status.getDescription(),
                                            latencyMs);
                                }

                                super.onClose(status, trailers);
                            }
                        },
                        headers);
            }
        };
    }
}
