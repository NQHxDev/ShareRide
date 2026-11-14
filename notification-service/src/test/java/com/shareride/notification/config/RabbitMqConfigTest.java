package com.shareride.notification.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

class RabbitMqConfigTest {

    private RabbitMqConfig rabbitMqConfig;

    @BeforeEach
    void setUp() {
        rabbitMqConfig = new RabbitMqConfig();
        ReflectionTestUtils.setField(rabbitMqConfig, "exchangeName", "test.exchange");
        ReflectionTestUtils.setField(rabbitMqConfig, "queueName", "test.queue");
        ReflectionTestUtils.setField(rabbitMqConfig, "routingKey", "test.routing.key");
        ReflectionTestUtils.setField(rabbitMqConfig, "dlxName", "test.dlx");
        ReflectionTestUtils.setField(rabbitMqConfig, "dlqName", "test.dlq");
    }

    @Test
    @DisplayName("Should create and configure all AMQP beans properly")
    void testRabbitMqBeansCreation() {
        TopicExchange exchange = rabbitMqConfig.notificationExchange();
        assertNotNull(exchange);
        assertEquals("test.exchange", exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());

        TopicExchange dlx = rabbitMqConfig.notificationDlx();
        assertNotNull(dlx);
        assertEquals("test.dlx", dlx.getName());

        Queue queue = rabbitMqConfig.notificationQueue();
        assertNotNull(queue);
        assertEquals("test.queue", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("test.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("notification.dlq", queue.getArguments().get("x-dead-letter-routing-key"));

        Queue dlq = rabbitMqConfig.notificationDlq();
        assertNotNull(dlq);
        assertEquals("test.dlq", dlq.getName());
        assertTrue(dlq.isDurable());

        Binding notificationBinding = rabbitMqConfig.notificationBinding(queue, exchange);
        assertNotNull(notificationBinding);
        assertEquals("test.routing.key", notificationBinding.getRoutingKey());

        Binding dlqBinding = rabbitMqConfig.dlqBinding(dlq, dlx);
        assertNotNull(dlqBinding);
        assertEquals("notification.dlq", dlqBinding.getRoutingKey());

        MessageConverter converter = rabbitMqConfig.messageConverter();
        assertNotNull(converter);
        assertTrue(converter instanceof JacksonJsonMessageConverter);

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        SimpleRabbitListenerContainerFactory factory =
                rabbitMqConfig.rabbitListenerContainerFactory(connectionFactory, converter);
        assertNotNull(factory);
    }
}
