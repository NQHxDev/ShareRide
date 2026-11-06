package com.shareride.common.communication.grpc.interceptor;

import io.grpc.*;
import java.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side gRPC interceptor logging incoming RPC requests, peer address, execution duration, and status.
 */
public class GrpcServerLoggingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        final long start = System.currentTimeMillis();
        final String methodName = call.getMethodDescriptor().getFullMethodName();
        final SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);

        ServerCall<ReqT, RespT> forwardingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long duration = System.currentTimeMillis() - start;
                if (status.isOk()) {
                    log.info("[gRPC Server] {} status={} duration={}ms peer={}",
                            methodName, status.getCode(), duration, remoteAddr);
                } else {
                    log.warn("[gRPC Server] {} status={} duration={}ms description={} peer={}",
                            methodName, status.getCode(), duration, status.getDescription(), remoteAddr);
                }
                super.close(status, trailers);
            }
        };

        return next.startCall(forwardingCall, headers);
    }
}
