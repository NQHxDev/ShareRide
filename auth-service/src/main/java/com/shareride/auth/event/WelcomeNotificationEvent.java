package com.shareride.auth.event;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event DTO cho WELCOME notification dispatched qua RabbitMQ khi account được activated. Schema
 * tuân thủ specs/004-email-verification-welcome/contracts/WelcomeNotificationEvent.json.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelcomeNotificationEvent {

    private String eventId;

    private String recipientEmail;

    private String notificationType;

    private String locale;

    private Map<String, Object> parameters;
}
