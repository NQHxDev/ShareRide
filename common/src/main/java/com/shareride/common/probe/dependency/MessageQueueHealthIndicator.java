package com.shareride.common.probe.dependency;

import java.lang.reflect.Method;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationContext;

/**
 * Enterprise Message Queue health indicator (Kafka / RabbitMQ) protected by timeouts and circuit
 * breaker.
 */
public class MessageQueueHealthIndicator implements HealthIndicator {

    private final ApplicationContext applicationContext;
    private final ResilientHealthExecutor resilientExecutor;

    public MessageQueueHealthIndicator(ApplicationContext applicationContext) {
        this(applicationContext, new ResilientHealthExecutor("message-queue", 2000L));
    }

    public MessageQueueHealthIndicator(
            ApplicationContext applicationContext, ResilientHealthExecutor resilientExecutor) {
        this.applicationContext = applicationContext;
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public Health health() {
        Object mqBean = findMessageQueueBean();
        if (mqBean == null) {
            return Health.up()
                    .withDetail("messageQueue", "NOT_CONFIGURED")
                    .withDetail(
                            "message",
                            "No Kafka or RabbitMQ components configured for this microservice")
                    .build();
        }

        return resilientExecutor.execute(() -> checkMessageQueue(mqBean));
    }

    private Health checkMessageQueue(Object mqBean) {
        String beanClassName = mqBean.getClass().getName();
        Health.Builder builder = Health.up();

        try {
            if (beanClassName.contains("kafka")) {
                builder.withDetail("type", "Apache Kafka");
                // Check if KafkaAdmin or KafkaTemplate
                try {
                    Method describeMethod = mqBean.getClass().getMethod("describeCluster");
                    Object clusterDesc = describeMethod.invoke(mqBean);
                    builder.withDetail("cluster", String.valueOf(clusterDesc));
                } catch (NoSuchMethodException ignored) {
                    builder.withDetail("status", "KAFKA_CLIENT_AVAILABLE");
                }
            } else if (beanClassName.contains("rabbit") || beanClassName.contains("amqp")) {
                builder.withDetail("type", "RabbitMQ / AMQP");
                builder.withDetail("status", "RABBITMQ_CLIENT_AVAILABLE");
            } else {
                builder.withDetail("type", beanClassName);
                builder.withDetail("status", "MQ_CONNECTED");
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }

    private Object findMessageQueueBean() {
        if (applicationContext == null) {
            return null;
        }

        // Check for KafkaAdmin or KafkaTemplate
        try {
            Class<?> kafkaClass = Class.forName("org.springframework.kafka.core.KafkaAdmin");
            String[] names = applicationContext.getBeanNamesForType(kafkaClass);
            if (names.length > 0) {
                return applicationContext.getBean(names[0]);
            }
        } catch (Throwable ignored) {
        }

        // Check for RabbitTemplate
        try {
            Class<?> rabbitClass =
                    Class.forName("org.springframework.amqp.rabbit.core.RabbitTemplate");
            String[] names = applicationContext.getBeanNamesForType(rabbitClass);
            if (names.length > 0) {
                return applicationContext.getBean(names[0]);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
