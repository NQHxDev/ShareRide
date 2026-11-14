package com.shareride.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.NotificationDispatchEvent;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for notification dispatch events with idempotency protection and automatic DLQ
 * routing on unrecoverable failures.
 *
 * <p>Ensures zero duplicate emails by checking event ID before processing via {@link
 * IdempotentMessageService}.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final IdempotentMessageService idempotentMessageService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(
            IdempotentMessageService idempotentMessageService,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.idempotentMessageService = idempotentMessageService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes notification dispatch events from RabbitMQ queue with idempotency guard.
     *
     * <p>Duplicate events (same eventId) are automatically skipped to prevent duplicate email
     * sends.
     *
     * <p>Unrecoverable exceptions are routed to DLQ via RabbitMQ automatic dead-lettering.
     *
     * @param message JSON payload of NotificationDispatchEvent
     */
    @RabbitListener(queues = "shareride.notification.queue")
    public void handleNotificationEvent(String message) {
        try {
            NotificationDispatchEvent event =
                    objectMapper.readValue(message, NotificationDispatchEvent.class);

            String eventId = event.getEventId().toString();

            // Idempotency guard: Skip if already processed
            if (!idempotentMessageService.markAsProcessed(eventId)) {
                log.warn(
                        "Duplicate notification event detected [eventId={}]. Skipping to prevent"
                                + " duplicate email.",
                        eventId);
                return;
            }

            log.info(
                    "Processing notification event [eventId={}, type={}, recipient={}]",
                    eventId,
                    event.getNotificationType(),
                    event.getRecipientEmail());

            // Convert event to request for service layer
            NotificationDispatchRequest request =
                    NotificationDispatchRequest.builder()
                            .eventId(event.getEventId())
                            .recipientEmail(event.getRecipientEmail())
                            .notificationType(NotificationType.valueOf(event.getNotificationType()))
                            .locale(event.getLocale())
                            .parameters(event.getParameters())
                            .build();

            // Dispatch notification via service layer
            notificationService.dispatchNotification(request);

            log.info("Successfully processed notification event [eventId={}]", eventId);

        } catch (Exception e) {
            log.error(
                    "Failed to process notification event. Message will be routed to DLQ: {}",
                    e.getMessage(),
                    e);
            // Re-throw to trigger RabbitMQ DLQ routing
            throw new RuntimeException("Notification event processing failed", e);
        }
    }
}
