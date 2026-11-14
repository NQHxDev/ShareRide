package com.shareride.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shareride.notification.domain.NotificationStatus;
import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.dto.NotificationStatusResponse;
import com.shareride.notification.dto.TestNotificationRequest;
import com.shareride.notification.service.NotificationService;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for NotificationController.
 *
 * <p>Note: Full request/response validation with @WebMvcTest requires
 * ApiVersioningAutoConfiguration and is covered by integration tests. These unit tests verify the
 * controller delegates correctly to the service layer.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;

    @InjectMocks private NotificationController notificationController;

    @Test
    @Disabled("Requires full WebMvcTest setup with ApiVersioning - covered by integration tests")
    void testSendNotification_shouldReturn201Created() {
        // Controller integration tests require ApiVersioningAutoConfiguration
        // Core logic is verified through NotificationServiceTest
    }

    @Test
    @Disabled("Requires full WebMvcTest setup with ApiVersioning - covered by integration tests")
    void getNotificationStatus_shouldReturn200Ok() {
        // Controller integration tests require ApiVersioningAutoConfiguration
        // Core logic is verified through NotificationServiceTest
    }

    @Test
    void testSendNotification_shouldDelegateToService() {
        UUID notificationId = UUID.randomUUID();
        TestNotificationRequest request =
                TestNotificationRequest.builder()
                        .recipientEmail("user@example.com")
                        .notificationType(NotificationType.ACCOUNT_VERIFICATION)
                        .locale("vi-VN")
                        .parameters(new HashMap<>())
                        .build();

        NotificationStatusResponse statusResponse =
                NotificationStatusResponse.builder()
                        .id(notificationId)
                        .eventId(UUID.randomUUID())
                        .recipientEmail("user@example.com")
                        .status(NotificationStatus.SENT)
                        .providerMessageId("provider-msg-id")
                        .retryCount(0)
                        .sentAt(Instant.now())
                        .createdAt(Instant.now())
                        .build();

        when(notificationService.dispatchNotification(any(NotificationDispatchRequest.class)))
                .thenReturn(notificationId);
        when(notificationService.getNotificationStatus(notificationId)).thenReturn(statusResponse);

        var response = notificationController.testSendNotification(request);

        assert response.getStatusCode().is2xxSuccessful();
        assert response.getBody() != null;
        assert response.getBody().getData().equals(statusResponse);
    }

    @Test
    void getNotificationStatus_shouldDelegateToService() {
        UUID notificationId = UUID.randomUUID();
        NotificationStatusResponse statusResponse =
                NotificationStatusResponse.builder()
                        .id(notificationId)
                        .status(NotificationStatus.SENT)
                        .recipientEmail("user@example.com")
                        .build();

        when(notificationService.getNotificationStatus(notificationId)).thenReturn(statusResponse);

        var response = notificationController.getNotificationStatus(notificationId);

        assert response.getStatusCode().is2xxSuccessful();
        assert response.getBody() != null;
        assert response.getBody().getData().equals(statusResponse);
    }
}
