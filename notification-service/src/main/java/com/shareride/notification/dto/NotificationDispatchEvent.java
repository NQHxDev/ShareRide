package com.shareride.notification.dto;

import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event payload for notification dispatch requests from upstream services via RabbitMQ.
 *
 * <p>Contract matches {@code contracts/NotificationDispatchEvent.json} schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDispatchEvent {

    /** Unique event identifier (UUIDv7) used for idempotency. */
    private UUID eventId;

    /** Destination email address for the recipient. */
    private String recipientEmail;

    /** Target email notification type (ACCOUNT_VERIFICATION, PASSWORD_RESET). */
    private String notificationType;

    /** Preferred locale of recipient (e.g., 'vi-VN', 'en-US'). Defaults to 'vi-VN'. */
    private String locale;

    /** Dynamic template variables required by the specific template. */
    private Map<String, Object> parameters;
}
