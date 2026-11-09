package com.shareride.common.communication.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import java.util.concurrent.TimeUnit;

/**
 * Client-side gRPC interceptor that sets a default call deadline if none is already specified by
 * the caller.
 */
public class GrpcClientDeadlineInterceptor implements ClientInterceptor {

    private final long defaultTimeoutMs;

    public GrpcClientDeadlineInterceptor() {
        this(3000L);
    }

    public GrpcClientDeadlineInterceptor(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 3000L;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        if (callOptions.getDeadline() == null) {
            callOptions = callOptions.withDeadlineAfter(defaultTimeoutMs, TimeUnit.MILLISECONDS);
        }

        return next.newCall(method, callOptions);
    }
}
