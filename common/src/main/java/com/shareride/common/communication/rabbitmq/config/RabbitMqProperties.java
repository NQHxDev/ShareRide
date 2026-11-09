package com.shareride.common.communication.rabbitmq.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for RabbitMQ communication and Dead-Letter topology. */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.rabbitmq")
public class RabbitMqProperties {

    private String defaultExchange = "shareride.topic";
    private String deadLetterExchange = "shareride.dlx";
    private String deadLetterQueue = "shareride.dlq";
    private String deadLetterRoutingKey = "shareride.dlq.routingKey";
    private int maxRetryAttempts = 3;
    private long initialRetryIntervalMs = 1000L;
    private double retryMultiplier = 2.0d;
    private long maxRetryIntervalMs = 10000L;
}
