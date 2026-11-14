package com.shareride.notification.dto;

import com.shareride.notification.domain.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestNotificationRequest {

    @NotBlank(message = "Recipient email must not be blank")
    @Email(message = "Recipient email must be a valid email address")
    private String recipientEmail;

    @NotNull(message = "Notification type must not be null")
    private NotificationType notificationType;

    // Locale is optional - allows graceful fallback to default vi-VN per FR-007
    private String locale;

    @NotNull(message = "Parameters must not be null")
    private Map<String, Object> parameters;
}
