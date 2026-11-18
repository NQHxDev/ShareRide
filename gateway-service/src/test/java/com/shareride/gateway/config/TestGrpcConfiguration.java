package com.shareride.gateway.config;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.client.GrpcClientProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing a {@link GrpcChannelFactory} for integration tests that load the
 * full Spring context without starting actual gRPC servers.
 *
 * <p>This factory uses default properties and allows the application context to start successfully
 * in test mode even though no actual gRPC servers are running.
 */
@TestConfiguration
public class TestGrpcConfiguration {

    /**
     * Provides a {@link GrpcChannelFactory} with default test properties.
     *
     * @return gRPC channel factory for testing
     */
    @Bean
    @Primary
    public GrpcChannelFactory testGrpcChannelFactory() {
        return new GrpcChannelFactory(new GrpcClientProperties());
    }
}
