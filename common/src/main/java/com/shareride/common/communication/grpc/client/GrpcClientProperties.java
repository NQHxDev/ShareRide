package com.shareride.common.communication.grpc.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for gRPC client connections. Binds to {@code shareride.grpc.client.*}
 * configuration namespace.
 */
@Data
@ConfigurationProperties(prefix = "shareride.grpc.client")
public class GrpcClientProperties {

    private long defaultDeadlineMs = 5000;

    private int keepAliveTimeSeconds = 60;

    private int keepAliveTimeoutSeconds = 20;

    private boolean keepAliveWithoutCalls = false;

    private int maxInboundMessageSize = 10485760;

    private int idleTimeoutSeconds = 300;

    private int maxRetryAttempts = 3;

    private boolean enableRetry = true;

    private long initialBackoffMs = 1000;

    private long maxBackoffMs = 10000;

    private double backoffMultiplier = 1.5;
}
