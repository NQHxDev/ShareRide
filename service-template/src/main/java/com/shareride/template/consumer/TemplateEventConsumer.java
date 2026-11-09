package com.shareride.template.consumer;

import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.common.communication.rabbitmq.event.ShareRideEvent;
import com.shareride.template.constant.TemplateConstants;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Enterprise standard RabbitMQ Message Consumer demonstrating:
 *
 * <ul>
 *   <li>Idempotent message consumption (deduplication via Redis)
 *   <li>Distributed tracing context propagation
 *   <li>Resilient error recovery with Dead Letter Queue (DLQ) integration
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateEventConsumer {

    private final IdempotentMessageService idempotentMessageService;

    private static final Duration DEDUPLICATION_TTL = Duration.ofHours(24);

    @RabbitListener(queues = TemplateConstants.RabbitMq.QUEUE_TEMPLATE_EVENTS)
    public void handleTemplateEvent(ShareRideEvent<Map<String, Object>> event) {
        if (event == null || event.getEventId() == null) {
            log.warn("Received malformed or null event");
            return;
        }

        // 1. Idempotency Check (Redis-backed deduplication)
        if (idempotentMessageService.isProcessed(event.getEventId())) {
            log.warn(
                    "Duplicate event detected [id={}, type={}]. Skipping execution.",
                    event.getEventId(),
                    event.getEventType());
            return;
        }

        log.info(
                "Processing event [id={}, type={}, aggregateId={}, producer={}, traceId={}]",
                event.getEventId(),
                event.getEventType(),
                event.getAggregateId(),
                event.getProducer(),
                event.getTraceId());

        try {
            // 2. Business Logic Execution
            processEventPayload(event.getEventType(), event.getPayload());

            // 3. Mark processed in Redis
            idempotentMessageService.markAsProcessed(event.getEventId(), DEDUPLICATION_TTL);
            log.info("Successfully processed event [id={}]", event.getEventId());
        } catch (Exception e) {
            log.error(
                    "Failed to process event [id={}]: {}. Message will be routed to DLQ.",
                    event.getEventId(),
                    e.getMessage(),
                    e);
            throw e; // Rethrow to trigger RabbitMQ DLQ routing
        }
    }

    private void processEventPayload(String eventType, Map<String, Object> payload) {
        log.info("Handling event payload for type [{}]: {}", eventType, payload);
    }
}
