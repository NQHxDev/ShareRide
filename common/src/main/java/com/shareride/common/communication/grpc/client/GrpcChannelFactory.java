package com.shareride.common.communication.grpc.client;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

/**
 * Factory managing high-performance {@link ManagedChannel} instances for synchronous internal gRPC
 * calls. Provides connection pooling, HTTP/2 multiplexing, keep-alive configuration, and automatic
 * retry with exponential backoff. Channels are cached per target and reused across calls.
 *
 * <p>Automatically attaches global client interceptors for tracing, logging, and deadline
 * enforcement.
 */
@Slf4j
public class GrpcChannelFactory implements DisposableBean {

    private final GrpcClientProperties properties;
    private final List<ClientInterceptor> globalInterceptors = new ArrayList<>();
    private final ConcurrentMap<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    public GrpcChannelFactory(GrpcClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Obtains or creates a cached, resilient {@link Channel} targeting the specified target service
     * or address. If the target is formatted as "host:port", it connects to that host and port.
     * Otherwise, target is treated as a target URI or service name (e.g. "auth-service").
     *
     * @param target Target service hostname or target address (e.g., "auth-service",
     *     "localhost:9081")
     * @return Prepared {@link Channel} with global interceptors attached
     */
    public Channel getChannel(String target) {
        return getManagedChannel(target);
    }

    /**
     * Obtains or creates a cached {@link ManagedChannel} targeting the specified target service or
     * address.
     *
     * @param target Target service hostname or target address (e.g., "auth-service",
     *     "localhost:9081")
     * @return Prepared {@link ManagedChannel} with global interceptors attached
     */
    public ManagedChannel getManagedChannel(String target) {
        if (target != null && target.contains(":")) {
            String[] parts = target.split(":", 2);
            try {
                int port = Integer.parseInt(parts[1]);
                return getManagedChannel(parts[0], port);
            } catch (NumberFormatException ignored) {
                // Not host:port format, proceed with target name
            }
        }
        return channelCache.computeIfAbsent(target, this::buildChannel);
    }

    /**
     * Obtains or creates a cached, resilient {@link Channel} targeting the specified host and port.
     *
     * @param host Target service hostname (e.g., "localhost", "auth-service")
     * @param port Target gRPC port (e.g., 9081)
     * @return Prepared {@link Channel} with global interceptors attached
     */
    public Channel getChannel(String host, int port) {
        return getManagedChannel(host, port);
    }

    /**
     * Obtains or creates a cached {@link ManagedChannel} targeting the specified host and port.
     *
     * @param host Target service hostname
     * @param port Target gRPC port
     * @return Prepared {@link ManagedChannel} with global interceptors attached
     */
    public ManagedChannel getManagedChannel(String host, int port) {
        String target = host + ":" + port;
        return channelCache.computeIfAbsent(target, t -> buildChannel(host, port));
    }

    private ManagedChannel buildChannel(String target) {
        log.info("Creating gRPC ManagedChannel for target [{}]", target);

        ManagedChannelBuilder<?> builder =
                ManagedChannelBuilder.forTarget(target)
                        .usePlaintext()
                        .maxInboundMessageSize(properties.getMaxInboundMessageSize())
                        .keepAliveTime(properties.getKeepAliveTimeSeconds(), TimeUnit.SECONDS)
                        .keepAliveTimeout(properties.getKeepAliveTimeoutSeconds(), TimeUnit.SECONDS)
                        .keepAliveWithoutCalls(properties.isKeepAliveWithoutCalls())
                        .idleTimeout(properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS);

        if (properties.isEnableRetry()) {
            builder.enableRetry()
                    .maxRetryAttempts(properties.getMaxRetryAttempts())
                    .retryBufferSize(8388608);
        }

        if (!globalInterceptors.isEmpty()) {
            builder.intercept(globalInterceptors);
        }

        return builder.build();
    }

    private ManagedChannel buildChannel(String host, int port) {
        String target = host + ":" + port;
        log.info("Creating gRPC ManagedChannel for target [{}]", target);

        ManagedChannelBuilder<?> builder =
                ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .maxInboundMessageSize(properties.getMaxInboundMessageSize())
                        .keepAliveTime(properties.getKeepAliveTimeSeconds(), TimeUnit.SECONDS)
                        .keepAliveTimeout(properties.getKeepAliveTimeoutSeconds(), TimeUnit.SECONDS)
                        .keepAliveWithoutCalls(properties.isKeepAliveWithoutCalls())
                        .idleTimeout(properties.getIdleTimeoutSeconds(), TimeUnit.SECONDS);

        if (properties.isEnableRetry()) {
            builder.enableRetry()
                    .maxRetryAttempts(properties.getMaxRetryAttempts())
                    .retryBufferSize(8388608);
        }

        if (!globalInterceptors.isEmpty()) {
            builder.intercept(globalInterceptors);
        }

        return builder.build();
    }

    /**
     * Registers a global {@link ClientInterceptor} that will be attached to all channels created by
     * this factory. Interceptors are applied in registration order.
     *
     * @param interceptor The interceptor to register
     */
    public void addGlobalInterceptor(ClientInterceptor interceptor) {
        if (interceptor != null) {
            this.globalInterceptors.add(interceptor);
            log.debug(
                    "Registered global gRPC client interceptor: {}",
                    interceptor.getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() {
        log.info("Shutting down {} cached gRPC channel(s)...", channelCache.size());
        channelCache.forEach(
                (target, channel) -> {
                    try {
                        channel.shutdown();
                        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                            log.warn(
                                    "gRPC channel [{}] did not terminate gracefully, forcing"
                                            + " shutdown",
                                    target);
                            channel.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        log.error("gRPC channel [{}] shutdown interrupted", target, e);
                        channel.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                });
        channelCache.clear();
        log.info("All gRPC channels shut down successfully");
    }
}
