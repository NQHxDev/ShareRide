package com.shareride.notification.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.outbox.OutboxMessage;
import com.shareride.common.outbox.OutboxStatus;
import com.shareride.notification.dto.NotificationDispatchEvent;
import com.shareride.notification.repository.OutboxMessageRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publisher for notification events supporting both Transactional Outbox pattern and direct async
 * broadcasting.
 *
 * <p>Transactional Outbox ensures reliable event publishing without dual-write hazards by storing
 * events in the database within the same transaction as domain changes.
 */
@Service
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);
    private static final String EXCHANGE_NAME = "shareride.notification.exchange";
    private static final String ROUTING_KEY = "notification.email.dispatch";

    private final OutboxMessageRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public NotificationEventPublisher(
            OutboxMessageRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes notification event using Transactional Outbox pattern.
     *
     * <p>Event is persisted in outbox table within current transaction and relayed asynchronously
     * by scheduled relay service.
     *
     * @param event Notification dispatch event to publish
     */
    @Transactional
    public void publishViaOutbox(NotificationDispatchEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxMessage outboxMessage =
                    OutboxMessage.builder()
                            .aggregateType("Notification")
                            .aggregateId(event.getEventId().toString())
                            .eventType("NotificationDispatchEvent")
                            .payload(payload)
                            .status(OutboxStatus.PENDING)
                            .retryCount(0)
                            .createdAt(Instant.now())
                            .build();

            outboxRepository.save(outboxMessage);

            log.info(
                    "Published notification event via Outbox [eventId={}, type={}]",
                    event.getEventId(),
                    event.getNotificationType());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification event to JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Event serialization failed", e);
        }
    }

    /**
     * Publishes notification event directly to RabbitMQ (async, fire-and-forget).
     *
     * <p>Use this for non-critical notifications or when transactional guarantees are not required.
     *
     * @param event Notification dispatch event to publish
     */
    public void publishDirect(NotificationDispatchEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, event);

            log.info(
                    "Published notification event directly [eventId={}, type={}]",
                    event.getEventId(),
                    event.getNotificationType());

        } catch (Exception e) {
            log.error(
                    "Failed to publish notification event directly [eventId={}]: {}",
                    event.getEventId(),
                    e.getMessage(),
                    e);
            throw new RuntimeException("Direct event publishing failed", e);
        }
    }
}
