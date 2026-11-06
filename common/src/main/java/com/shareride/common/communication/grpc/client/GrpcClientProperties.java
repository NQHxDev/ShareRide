package com.shareride.common.communication.grpc.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for outgoing gRPC client channels.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.grpc")
public class GrpcClientProperties {

    private Map<String, ChannelConfig> channels = new HashMap<>();
    private ChannelConfig defaults = new ChannelConfig();

    @Getter
    @Setter
    public static class ChannelConfig {
        private String address = "localhost:9090";
        private boolean plaintext = true;
        private long timeoutMs = 3000L;
        private long keepAliveTimeSeconds = 60L;
        private int maxInboundMessageSize = 10 * 1024 * 1024; // 10MB
    }

    public ChannelConfig getChannelConfig(String serviceName) {
        return channels.getOrDefault(serviceName, defaults);
    }
}
