package com.shareride.template.publisher;

import com.shareride.common.communication.rabbitmq.event.ShareRideEvent;
import com.shareride.common.communication.rabbitmq.publisher.RabbitMqEventPublisher;
import com.shareride.common.outbox.OutboxService;
import com.shareride.template.constant.TemplateConstants;
import com.shareride.template.domain.TemplateItem;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Enterprise standard Event Publisher providing both:
 *
 * <ul>
 *   <li><b>Transactional Outbox:</b> Guaranteed at-least-once delivery with PostgreSQL ACID
 *       atomicity.
 *   <li><b>Direct Asynchronous RabbitMQ Publishing:</b> Non-blocking fire-and-forget for
 *       lightweight notifications.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateEventPublisher {

    private final OutboxService outboxService;
    private final RabbitMqEventPublisher rabbitMqEventPublisher;

    /**
     * Records a Transactional Outbox event in the same ACID transaction as the entity mutation. The
     * OutboxRelayScheduler will safely poll and push it to RabbitMQ.
     */
    public void recordItemCreatedOutbox(TemplateItem item) {
        outboxService.recordEvent(
                "TEMPLATE_ITEM",
                item.getId().toString(),
                "TEMPLATE_ITEM_CREATED",
                Map.of(
                        "id", item.getId(),
                        "code", item.getCode(),
                        "name", item.getName(),
                        "status", item.getStatus().name()));
        log.info("Recorded Transactional Outbox event for created item [{}]", item.getId());
    }

    /**
     * Publishes a non-blocking asynchronous event directly over RabbitMQ with automatic distributed
     * tracing.
     */
    public CompletableFuture<Void> publishItemCreatedDirect(TemplateItem item) {
        ShareRideEvent<Map<String, Object>> event =
                ShareRideEvent.create(
                        TemplateConstants.RabbitMq.ROUTING_KEY_ITEM_CREATED,
                        "TEMPLATE_ITEM",
                        item.getId().toString(),
                        TemplateConstants.RabbitMq.SERVICE_NAME,
                        Map.of(
                                "id", item.getId(),
                                "code", item.getCode(),
                                "name", item.getName(),
                                "status", item.getStatus().name()));

        return rabbitMqEventPublisher.publishAsync(
                TemplateConstants.RabbitMq.EXCHANGE_EVENTS,
                TemplateConstants.RabbitMq.ROUTING_KEY_ITEM_CREATED,
                event);
    }

    /** Records a Transactional Outbox event for updated item. */
    public void recordItemUpdatedOutbox(TemplateItem item) {
        outboxService.recordEvent(
                "TEMPLATE_ITEM",
                item.getId().toString(),
                "TEMPLATE_ITEM_UPDATED",
                Map.of(
                        "id", item.getId(),
                        "code", item.getCode(),
                        "name", item.getName(),
                        "status", item.getStatus().name()));
        log.info("Recorded Transactional Outbox event for updated item [{}]", item.getId());
    }

    /** Records a Transactional Outbox event for deleted item. */
    public void recordItemDeletedOutbox(UUID itemId, String code) {
        outboxService.recordEvent(
                "TEMPLATE_ITEM",
                itemId.toString(),
                "TEMPLATE_ITEM_DELETED",
                Map.of("id", itemId, "code", code));
        log.info("Recorded Transactional Outbox event for deleted item [{}]", itemId);
    }
}
