package com.shareride.template.config;

import com.shareride.common.communication.rabbitmq.config.RabbitMqTopologyHelper;
import com.shareride.template.constant.TemplateConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enterprise standard RabbitMQ topology configuration for Template Service. Sets up Topic Exchange,
 * Resilient Queue with Dead Letter Exchange (DLX) routing, and Bindings.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange templateTopicExchange() {
        return RabbitMqTopologyHelper.createTopicExchange(
                TemplateConstants.RabbitMq.EXCHANGE_EVENTS);
    }

    @Bean
    public DirectExchange templateDeadLetterExchange() {
        return RabbitMqTopologyHelper.createDirectExchange(TemplateConstants.RabbitMq.EXCHANGE_DLX);
    }

    @Bean
    public Queue templateEventQueue() {
        return RabbitMqTopologyHelper.createResilientQueue(
                TemplateConstants.RabbitMq.QUEUE_TEMPLATE_EVENTS,
                TemplateConstants.RabbitMq.EXCHANGE_DLX,
                TemplateConstants.RabbitMq.ROUTING_KEY_DLQ);
    }

    @Bean
    public Queue templateDeadLetterQueue() {
        return new Queue(TemplateConstants.RabbitMq.QUEUE_TEMPLATE_EVENTS_DLQ, true);
    }

    @Bean
    public Binding templateEventBinding(
            Queue templateEventQueue, TopicExchange templateTopicExchange) {
        return RabbitMqTopologyHelper.bindQueueToTopic(
                templateEventQueue, templateTopicExchange, "template.item.*");
    }

    @Bean
    public Binding templateDeadLetterBinding(
            Queue templateDeadLetterQueue, DirectExchange templateDeadLetterExchange) {
        return RabbitMqTopologyHelper.bindDeadLetterQueue(
                templateDeadLetterQueue,
                templateDeadLetterExchange,
                TemplateConstants.RabbitMq.ROUTING_KEY_DLQ);
    }
}
