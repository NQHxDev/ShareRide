package com.shareride.common.communication.grpc.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for gRPC server runtime. Binds to {@code shareride.grpc.server.*}
 * configuration namespace.
 */
@Data
@ConfigurationProperties(prefix = "shareride.grpc.server")
public class GrpcServerProperties {

    private boolean enabled = true;

    private int port = 9090;

    private int maxInboundMessageSize = 10485760;

    private int keepAliveTimeSeconds = 60;

    private int keepAliveTimeoutSeconds = 20;

    private int permitKeepAliveTimeSeconds = 30;

    private boolean permitKeepAliveWithoutCalls = false;

    private int shutdownGracePeriodSeconds = 5;

    private int maxConnectionIdleSeconds = 300;

    private int maxConnectionAgeSeconds = 3600;

    private int maxConnectionAgeGraceSeconds = 120;
}
