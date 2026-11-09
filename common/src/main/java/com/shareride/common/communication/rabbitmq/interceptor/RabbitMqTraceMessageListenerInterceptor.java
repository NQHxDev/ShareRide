package com.shareride.common.communication.rabbitmq.interceptor;

import com.shareride.common.telemetry.MessagingContextPropagator;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;

/**
 * AOP Interceptor for RabbitMQ Message Listeners that extracts distributed trace headers and
 * UserContext before listener execution, ensuring thread-local cleanup in all cases.
 */
public class RabbitMqTraceMessageListenerInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        Message message = null;

        for (Object arg : args) {
            if (arg instanceof Message msg) {
                message = msg;
                break;
            }
        }

        if (message != null && message.getMessageProperties() != null) {
            MessagingContextPropagator.extract(message.getMessageProperties().getHeaders());
        }

        try {
            return invocation.proceed();
        } finally {
            MessagingContextPropagator.clear();
        }
    }
}
