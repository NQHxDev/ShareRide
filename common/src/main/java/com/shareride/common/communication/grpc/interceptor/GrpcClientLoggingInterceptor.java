package com.shareride.common.communication.grpc.interceptor;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side gRPC interceptor logging method calls, latency duration, and response status.
 */
public class GrpcClientLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientLoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        final long start = System.currentTimeMillis();

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long duration = System.currentTimeMillis() - start;
                        if (status.isOk()) {
                            log.debug("[gRPC Client] {} completed in {}ms status={}",
                                    method.getFullMethodName(), duration, status.getCode());
                        } else {
                            log.warn("[gRPC Client] {} FAILED in {}ms status={} description={}",
                                    method.getFullMethodName(), duration, status.getCode(), status.getDescription());
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
