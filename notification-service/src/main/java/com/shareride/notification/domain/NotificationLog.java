package com.shareride.notification.domain;

import com.shareride.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends AuditableEntity {

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationStatus status;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "masked_context_data", columnDefinition = "TEXT")
    private String maskedContextData;

    @Column(name = "sent_at")
    private Instant sentAt;
}
