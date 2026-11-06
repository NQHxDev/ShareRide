package com.shareride.common.communication.rabbitmq.config;

import org.springframework.amqp.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper utility for microservices to build standard resilient RabbitMQ topology
 * (Exchanges, Resilient Queues with Dead-Letter routing, and Bindings).
 */
public final class RabbitMqTopologyHelper {

    private RabbitMqTopologyHelper() {
    }

    /**
     * Creates a durable Queue configured with Dead Letter Exchange (DLX) parameters.
     * When messages fail max retries, RabbitMQ automatically routes them to the DLX.
     */
    public static Queue createResilientQueue(String queueName, String deadLetterExchange, String deadLetterRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", deadLetterExchange);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey);
        return new Queue(queueName, true, false, false, args);
    }

    /**
     * Creates a standard durable TopicExchange.
     */
    public static TopicExchange createTopicExchange(String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Creates a standard durable DirectExchange (used for DLX).
     */
    public static DirectExchange createDirectExchange(String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    /**
     * Creates a binding from a queue to a topic exchange with a routing key pattern.
     */
    public static Binding bindQueueToTopic(Queue queue, TopicExchange exchange, String routingKeyPattern) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKeyPattern);
    }

    /**
     * Creates a binding from a DLQ to the Dead Letter Exchange.
     */
    public static Binding bindDeadLetterQueue(Queue dlq, DirectExchange dlx, String deadLetterRoutingKey) {
        return BindingBuilder.bind(dlq).to(dlx).with(deadLetterRoutingKey);
    }
}
