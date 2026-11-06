package com.shareride.common.communication;

import com.shareride.common.communication.rabbitmq.config.RabbitMqTopologyHelper;
import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.common.communication.rabbitmq.event.ShareRideEvent;
import com.shareride.common.communication.rabbitmq.publisher.RabbitMqEventPublisher;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@DisplayName("RabbitMQ Asynchronous Communication & Idempotency Tests")
class RabbitMqCommunicationTest {

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Should create ShareRideEvent capturing active trace and user context automatically")
    void testCreateShareRideEventWithContext() {
        TraceIdContext.setTraceId("trace-order-12345");
        TraceIdContext.setSpanId("span-order-67890");

        UserContext userContext = UserContext.builder()
                .userId("u-customer-999")
                .tenantId("tenant-vn")
                .correlationId("corr-ride-request")
                .build();
        UserContextHolder.setContext(userContext);

        ShareRideEvent<String> event = ShareRideEvent.create(
                "RIDE_REQUESTED",
                "Ride",
                "ride-555",
                "gateway-service",
                "{\"pickup\":\"123 Le Loi\",\"destination\":\"456 Nguyen Hue\"}"
        );

        assertNotNull(event.getEventId());
        assertEquals("RIDE_REQUESTED", event.getEventType());
        assertEquals("Ride", event.getAggregateType());
        assertEquals("ride-555", event.getAggregateId());
        assertEquals("gateway-service", event.getProducer());
        assertEquals("trace-order-12345", event.getTraceId());
        assertEquals("span-order-67890", event.getSpanId());
        assertEquals("u-customer-999", event.getUserId());
        assertEquals("tenant-vn", event.getTenantId());
        assertEquals("corr-ride-request", event.getCorrelationId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("RabbitMqEventPublisher should publish async without blocking caller thread")
    void testAsyncPublishFireAndForget() throws Exception {
        RabbitTemplate mockRabbitTemplate = Mockito.mock(RabbitTemplate.class);
        RabbitMqEventPublisher publisher = new RabbitMqEventPublisher(mockRabbitTemplate);

        ShareRideEvent<String> event = ShareRideEvent.create(
                "TRIP_STATUS_UPDATED", "Trip", "trip-1", "ride-service", "STARTED"
        );

        CompletableFuture<Void> future = publisher.publishAsync("shareride.topic", "trip.status.started", event);
        assertNotNull(future);
        future.get(); // Wait for completion

        Mockito.verify(mockRabbitTemplate).convertAndSend(
                eq("shareride.topic"),
                eq("trip.status.started"),
                eq(event),
                any(org.springframework.amqp.core.MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("IdempotentMessageService should prevent duplicate message processing")
    void testIdempotentConsumerDeduplication() {
        IdempotentMessageService idempotency = new IdempotentMessageService(); // in-memory fallback
        String eventId = "evt-uuid-99999";

        assertFalse(idempotency.isProcessed(eventId));

        // First attempt: should execute
        AtomicBoolean executed1 = new AtomicBoolean(false);
        boolean firstRun = idempotency.executeIfNew(eventId, () -> executed1.set(true));
        assertTrue(firstRun);
        assertTrue(executed1.get());
        assertTrue(idempotency.isProcessed(eventId));

        // Duplicate delivery (e.g. RabbitMQ retry): should SKIP execution!
        AtomicBoolean executed2 = new AtomicBoolean(false);
        boolean secondRun = idempotency.executeIfNew(eventId, () -> executed2.set(true));
        assertFalse(secondRun, "Duplicate event must be skipped to ensure consumer idempotency");
        assertFalse(executed2.get());
    }

    @Test
    @DisplayName("RabbitMqTopologyHelper should build resilient queues with DLX/DLQ configurations")
    void testTopologyHelper() {
        Queue queue = RabbitMqTopologyHelper.createResilientQueue("order-queue", "shareride.dlx", "order.dlq");
        assertNotNull(queue);
        assertEquals("order-queue", queue.getName());
        assertEquals("shareride.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("order.dlq", queue.getArguments().get("x-dead-letter-routing-key"));

        TopicExchange topicExchange = RabbitMqTopologyHelper.createTopicExchange("shareride.topic");
        DirectExchange dlx = RabbitMqTopologyHelper.createDirectExchange("shareride.dlx");
        assertNotNull(dlx);
        assertEquals("shareride.dlx", dlx.getName());

        Binding topicBinding = RabbitMqTopologyHelper.bindQueueToTopic(queue, topicExchange, "order.*");
        assertNotNull(topicBinding);
        assertEquals("shareride.topic", topicBinding.getExchange());

        Binding dlqBinding = RabbitMqTopologyHelper.bindDeadLetterQueue(new Queue("order-dlq"), dlx, "order.dlq");
        assertNotNull(dlqBinding);
        assertEquals("shareride.dlx", dlqBinding.getExchange());
    }
}
