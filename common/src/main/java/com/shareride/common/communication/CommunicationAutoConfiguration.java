package com.shareride.common.communication;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.client.GrpcClientProperties;
import com.shareride.common.communication.grpc.interceptor.GrpcClientDeadlineInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcClientLoggingInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcClientTraceInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcServerExceptionInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcServerLoggingInterceptor;
import com.shareride.common.communication.grpc.interceptor.GrpcServerTraceInterceptor;
import com.shareride.common.communication.grpc.server.GrpcServerLifecycle;
import com.shareride.common.communication.grpc.server.GrpcServerProperties;
import com.shareride.common.communication.rabbitmq.config.RabbitMqProperties;
import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.common.communication.rabbitmq.interceptor.RabbitMqTraceMessagePostProcessor;
import com.shareride.common.communication.rabbitmq.publisher.RabbitMqEventPublisher;
import com.shareride.common.communication.rest.RestTraceClientInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot AutoConfiguration wiring gRPC sync communication, RabbitMQ async messaging, and
 * distributed trace context propagation across Gateway and internal microservices.
 */
@AutoConfiguration
@EnableConfigurationProperties({
    GrpcClientProperties.class,
    GrpcServerProperties.class,
    RabbitMqProperties.class
})
public class CommunicationAutoConfiguration {

    // ----------------------------------------------------------
    // gRPC Synchronous Communication Beans
    // ----------------------------------------------------------

    // gRPC Client Beans
    @Bean
    @ConditionalOnMissingBean
    public GrpcChannelFactory grpcChannelFactory(
            GrpcClientProperties properties,
            GrpcClientTraceInterceptor traceInterceptor,
            GrpcClientLoggingInterceptor loggingInterceptor,
            GrpcClientDeadlineInterceptor deadlineInterceptor) {
        GrpcChannelFactory factory = new GrpcChannelFactory(properties);

        // Register client interceptors in order: Trace -> Deadline -> Logging
        factory.addGlobalInterceptor(traceInterceptor);
        factory.addGlobalInterceptor(deadlineInterceptor);
        factory.addGlobalInterceptor(loggingInterceptor);

        return factory;
    }

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

    // gRPC Server Beans
    @Bean
    @ConditionalOnMissingBean
    public GrpcServerTraceInterceptor grpcServerTraceInterceptor() {
        return new GrpcServerTraceInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public GrpcServerLoggingInterceptor grpcServerLoggingInterceptor() {
        return new GrpcServerLoggingInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public GrpcServerExceptionInterceptor grpcServerExceptionInterceptor() {
        return new GrpcServerExceptionInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "shareride.grpc.server",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public GrpcServerLifecycle grpcServerLifecycle(
            ApplicationContext applicationContext, GrpcServerProperties properties) {
        return new GrpcServerLifecycle(applicationContext, properties);
    }

    // ----------------------------------------------------------
    // RabbitMQ Asynchronous Communication Beans
    // ----------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter jacksonMessageConverter(
            @Autowired(required = false) JsonMapper jsonMapper) {
        return (jsonMapper != null)
                ? new JacksonJsonMessageConverter(jsonMapper)
                : new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitMqTraceMessagePostProcessor rabbitMqTraceMessagePostProcessor() {
        return new RabbitMqTraceMessagePostProcessor();
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            RabbitMqTraceMessagePostProcessor tracePostProcessor) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setBeforePublishPostProcessors(tracePostProcessor);
        return template;
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public RabbitMqEventPublisher rabbitMqEventPublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitMqEventPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentMessageService idempotentMessageService(
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        return new IdempotentMessageService(redisTemplate);
    }

    // ----------------------------------------------------------
    // REST External / Edge Communication Beans
    // ----------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public RestTraceClientInterceptor restTraceClientInterceptor() {
        return new RestTraceClientInterceptor();
    }
}
