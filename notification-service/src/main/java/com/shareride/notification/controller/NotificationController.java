package com.shareride.notification.controller;

import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.dto.NotificationStatusResponse;
import com.shareride.notification.dto.TestNotificationRequest;
import com.shareride.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
@ApiVersion("v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/notifications/test-send")
    public ResponseEntity<BaseResponse<NotificationStatusResponse>> testSendNotification(
            @Valid @RequestBody TestNotificationRequest request) {
        log.info("Received test notification request: {}", request);

        // Generate eventId if not provided (test endpoint behavior)
        UUID eventId = UUID.randomUUID();

        NotificationDispatchRequest dispatchRequest =
                NotificationDispatchRequest.builder()
                        .eventId(eventId)
                        .recipientEmail(request.getRecipientEmail())
                        .notificationType(request.getNotificationType())
                        .locale(request.getLocale())
                        .parameters(request.getParameters())
                        .build();

        UUID notificationId = notificationService.dispatchNotification(dispatchRequest);

        // Fetch the full status to return per contract
        NotificationStatusResponse status =
                notificationService.getNotificationStatus(notificationId);

        return ResponseEntity.ok(BaseResponse.success(status, "Gửi thông báo thành công"));
    }

    @GetMapping("/notifications/{id}/status")
    public ResponseEntity<BaseResponse<NotificationStatusResponse>> getNotificationStatus(
            @PathVariable("id") UUID id) {
        log.info("Fetching notification status for id: {}", id);

        NotificationStatusResponse status = notificationService.getNotificationStatus(id);

        return ResponseEntity.ok(
                BaseResponse.success(status, "Lấy trạng thái thông báo thành công"));
    }
}
