package com.shareride.common.communication.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC client interceptor that automatically applies a deadline to outgoing RPC calls if not
 * already set, preventing runaway execution and ensuring timely failure detection.
 *
 * <p>Default deadline is configurable via {@code shareride.grpc.client.default-deadline-ms}
 * property (default: 5000ms).
 */
@Slf4j
public class GrpcClientDeadlineInterceptor implements ClientInterceptor {

    private final long defaultDeadlineMs;

    public GrpcClientDeadlineInterceptor(long defaultDeadlineMs) {
        this.defaultDeadlineMs = defaultDeadlineMs;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        CallOptions effectiveOptions = callOptions;

        if (callOptions.getDeadline() == null) {
            Deadline deadline = Deadline.after(defaultDeadlineMs, TimeUnit.MILLISECONDS);
            effectiveOptions = callOptions.withDeadline(deadline);

            log.trace(
                    "Applied default deadline to gRPC call: method={}, deadlineMs={}",
                    method.getFullMethodName(),
                    defaultDeadlineMs);
        }

        return next.newCall(method, effectiveOptions);
    }
}
