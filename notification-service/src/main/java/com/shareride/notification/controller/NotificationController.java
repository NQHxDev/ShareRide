package com.shareride.notification.controller;

import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.dto.NotificationStatusResponse;
import com.shareride.notification.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<BaseResponse<UUID>> testSendNotification(
            @RequestBody NotificationDispatchRequest request) {
        log.info("Received test notification request: {}", request);

        UUID notificationId = notificationService.dispatchNotification(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(notificationId, "Notification dispatched successfully"));
    }

    @GetMapping("/notifications/{id}/status")
    public ResponseEntity<BaseResponse<NotificationStatusResponse>> getNotificationStatus(
            @PathVariable("id") UUID id) {
        log.info("Fetching notification status for id: {}", id);

        NotificationStatusResponse status = notificationService.getNotificationStatus(id);

        return ResponseEntity.ok(
                BaseResponse.success(status, "Notification status retrieved successfully"));
    }
}
