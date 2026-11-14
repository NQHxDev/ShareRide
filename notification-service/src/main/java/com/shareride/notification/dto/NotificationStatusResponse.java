package com.shareride.notification.dto;

import com.shareride.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatusResponse {

    private UUID id;
    private UUID eventId;
    private String recipientEmail;
    private NotificationStatus status;
    private String providerMessageId;
    private int retryCount;
    private String lastErrorMessage;
    private Instant sentAt;
    private Instant createdAt;
}
