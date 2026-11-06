package com.shareride.common.communication.rabbitmq.interceptor;

import com.shareride.common.telemetry.MessagingContextPropagator;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

/**
 * Spring AMQP {@link MessagePostProcessor} that injects W3C traceparent, trace-id,
 * and user/tenant isolation context into outgoing RabbitMQ message headers.
 */
public class RabbitMqTraceMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        if (message != null && message.getMessageProperties() != null) {
            MessageProperties props = message.getMessageProperties();
            MessagingContextPropagator.inject(props.getHeaders());
        }
        return message;
    }
}
