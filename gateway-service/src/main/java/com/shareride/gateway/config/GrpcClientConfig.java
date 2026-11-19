package com.shareride.gateway.config;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.client.GrpcClientProperties;
import com.shareride.common.communication.grpc.interceptor.GrpcClientDeadlineInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcClientLoggingInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcClientTraceInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC client configuration for API Gateway.
 *
 * <p>Initializes {@link GrpcChannelFactory} with distributed tracing, deadline, and logging
 * interceptors to facilitate high-performance reactive RPC communication with downstream
 * microservices (e.g. auth-service, template-service).
 */
@Configuration
@EnableConfigurationProperties(GrpcClientProperties.class)
public class GrpcClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public GrpcClientTraceInterceptor grpcClientTraceInterceptor() {
        return new GrpcClientTraceInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public GrpcClientLoggingInterceptor grpcClientLoggingInterceptor() {
        return new GrpcClientLoggingInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public GrpcClientDeadlineInterceptor grpcClientDeadlineInterceptor(
            GrpcClientProperties properties) {
        return new GrpcClientDeadlineInterceptor(properties.getDefaultDeadlineMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public GrpcChannelFactory grpcChannelFactory(
            GrpcClientProperties properties,
            GrpcClientTraceInterceptor traceInterceptor,
            GrpcClientLoggingInterceptor loggingInterceptor,
            GrpcClientDeadlineInterceptor deadlineInterceptor) {
        GrpcChannelFactory factory = new GrpcChannelFactory(properties);
        factory.addGlobalInterceptor(traceInterceptor);
        factory.addGlobalInterceptor(deadlineInterceptor);
        factory.addGlobalInterceptor(loggingInterceptor);
        return factory;
    }
}
