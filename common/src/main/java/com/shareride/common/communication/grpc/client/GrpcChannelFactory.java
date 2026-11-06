package com.shareride.common.communication.grpc.client;

import com.shareride.common.communication.grpc.interceptor.GrpcClientDeadlineInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcClientLoggingInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcClientTraceInterceptor;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Factory managing high-performance {@link ManagedChannel} instances for synchronous internal gRPC calls.
 * Automatically attaches client trace propagation, deadline enforcement, and structured logging interceptors.
 */
public class GrpcChannelFactory implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelFactory.class);

    private final GrpcClientProperties properties;
    private final List<ClientInterceptor> globalInterceptors = new ArrayList<>();
    private final ConcurrentMap<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    public GrpcChannelFactory(GrpcClientProperties properties) {
        this.properties = properties != null ? properties : new GrpcClientProperties();
        this.globalInterceptors.add(new GrpcClientTraceInterceptor());
        this.globalInterceptors.add(new GrpcClientLoggingInterceptor());
        this.globalInterceptors.add(new GrpcClientDeadlineInterceptor(
                this.properties.getDefaults().getTimeoutMs()
        ));
    }

    /**
     * Obtains or creates a cached, resilient {@link Channel} targeting the specified service.
     *
     * @param serviceName Service identifier (e.g. "auth-service", "ride-service", "billing-service")
     * @return Prepared {@link Channel}
     */
    public Channel getChannel(String serviceName) {
        return getManagedChannel(serviceName);
    }

    public ManagedChannel getManagedChannel(String serviceName) {
        return channelCache.computeIfAbsent(serviceName, this::buildChannel);
    }

    private ManagedChannel buildChannel(String serviceName) {
        GrpcClientProperties.ChannelConfig config = properties.getChannelConfig(serviceName);
        String target = config.getAddress();
        log.info("Creating gRPC ManagedChannel for service [{}] at target [{}]", serviceName, target);

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target)
                .maxInboundMessageSize(config.getMaxInboundMessageSize())
                .keepAliveTime(config.getKeepAliveTimeSeconds(), TimeUnit.SECONDS)
                .intercept(globalInterceptors);

        if (config.isPlaintext()) {
            builder.usePlaintext();
        }

        return builder.build();
    }

    public void addGlobalInterceptor(ClientInterceptor interceptor) {
        if (interceptor != null) {
            this.globalInterceptors.add(interceptor);
        }
    }

    @Override
    public void destroy() {
        log.info("Shutting down all cached gRPC channels...");
        channelCache.forEach((serviceName, channel) -> {
            try {
                channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        });
        channelCache.clear();
    }
}
