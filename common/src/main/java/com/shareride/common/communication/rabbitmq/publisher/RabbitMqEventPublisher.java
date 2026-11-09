package com.shareride.common.communication.rabbitmq.publisher;

import com.shareride.common.communication.rabbitmq.event.ShareRideEvent;
import com.shareride.common.telemetry.MessagingContextPropagator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * High-throughput, non-blocking Asynchronous Event Publisher over RabbitMQ. Dispatches messages
 * asynchronously so API Gateway and client callers are never blocked waiting.
 */
public class RabbitMqEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEventPublisher.class);

    private static final ExecutorService ASYNC_PUBLISHER_EXECUTOR =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread thread = new Thread(r, "rabbitmq-async-publisher");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public RabbitTemplate getRabbitTemplate() {
        return rabbitTemplate;
    }

    /**
     * Non-blocking asynchronous publish. Returns {@link CompletableFuture<Void>} immediately so
     * caller thread (e.g. Gateway HTTP request worker) never blocks.
     *
     * @param exchange Target RabbitMQ exchange
     * @param routingKey Routing key
     * @param event The event envelope
     * @return CompletableFuture completing once message is enqueued onto RabbitMQ channel
     */
    public CompletableFuture<Void> publishAsync(
            String exchange, String routingKey, ShareRideEvent<?> event) {
        return CompletableFuture.runAsync(
                () -> publish(exchange, routingKey, event), ASYNC_PUBLISHER_EXECUTOR);
    }

    /**
     * Synchronous fire-and-forget publish to RabbitMQ with automatic distributed trace injection.
     */
    public void publish(String exchange, String routingKey, ShareRideEvent<?> event) {
        if (event == null) {
            log.warn("Attempted to publish null event to exchange [{}]", exchange);
            return;
        }

        try {
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    event,
                    message -> {
                        MessagingContextPropagator.inject(
                                message.getMessageProperties().getHeaders());
                        if (event.getEventId() != null) {
                            message.getMessageProperties().setMessageId(event.getEventId());
                        }
                        if (event.getCorrelationId() != null) {
                            message.getMessageProperties()
                                    .setCorrelationId(event.getCorrelationId());
                        }
                        message.getMessageProperties().setContentType("application/json");
                        return message;
                    });

            log.info(
                    "Dispatched async event [type={}, id={}, aggregateId={}] to exchange [{}] with"
                            + " routingKey [{}]",
                    event.getEventType(),
                    event.getEventId(),
                    event.getAggregateId(),
                    exchange,
                    routingKey);
        } catch (Exception e) {
            log.error(
                    "Failed to publish async event [id={}] to exchange [{}]: {}",
                    event.getEventId(),
                    exchange,
                    e.getMessage(),
                    e);
            throw new RuntimeException("RabbitMQ publish error: " + e.getMessage(), e);
        }
    }
}
