package com.shareride.notification.dto;

import com.shareride.notification.domain.NotificationType;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDispatchRequest {

    private UUID eventId;
    private String recipientEmail;
    private NotificationType notificationType;
    private String locale;
    private Map<String, Object> parameters;
}
