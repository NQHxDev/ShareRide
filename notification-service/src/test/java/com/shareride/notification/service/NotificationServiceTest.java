package com.shareride.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.notification.client.ResendEmailClient;
import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.client.dto.ResendEmailResponse;
import com.shareride.notification.config.ResendProperties;
import com.shareride.notification.domain.NotificationLog;
import com.shareride.notification.domain.NotificationStatus;
import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.dto.NotificationStatusResponse;
import com.shareride.notification.dto.RenderedEmail;
import com.shareride.notification.repository.NotificationLogRepository;
import com.shareride.notification.service.impl.NotificationServiceImpl;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private EmailTemplateRenderer emailTemplateRenderer;
    @Mock private EmailMessageSourceService messageSourceService;
    @Mock private ResendEmailClient resendEmailClient;
    @Mock private ResendProperties resendProperties;

    private NotificationService notificationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        notificationService =
                new NotificationServiceImpl(
                        notificationLogRepository,
                        emailTemplateRenderer,
                        messageSourceService,
                        resendEmailClient,
                        resendProperties,
                        objectMapper);
    }

    @Test
    void dispatchNotification_shouldSendVerificationEmail() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> params = new HashMap<>();
        params.put("userName", "Nguyen Van A");
        params.put("verificationCode", "123456");

        NotificationDispatchRequest request =
                NotificationDispatchRequest.builder()
                        .eventId(eventId)
                        .recipientEmail("user@example.com")
                        .notificationType(NotificationType.ACCOUNT_VERIFICATION)
                        .locale("vi-VN")
                        .parameters(params)
                        .build();

        RenderedEmail renderedEmail =
                new RenderedEmail(
                        "<html>Verification Code: 123456</html>",
                        "Verification Code: 123456",
                        "email.verification.subject");

        ResendEmailResponse resendResponse = new ResendEmailResponse();
        resendResponse.setId("msg_test123");

        NotificationLog savedLog = new NotificationLog();
        savedLog.setId(UUID.randomUUID());

        when(notificationLogRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(messageSourceService.resolveLocale("vi-VN")).thenReturn(new Locale("vi"));
        when(emailTemplateRenderer.render(
                        eq(NotificationType.ACCOUNT_VERIFICATION), any(Locale.class), eq(params)))
                .thenReturn(renderedEmail);
        when(messageSourceService.getMessage(anyString(), any(Locale.class)))
                .thenReturn("Xác thực tài khoản của bạn");
        when(resendProperties.getFromEmail()).thenReturn("noreply@shareride.vn");
        when(resendEmailClient.sendEmail(any(ResendEmailRequest.class))).thenReturn(resendResponse);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(savedLog);

        UUID notificationId = notificationService.dispatchNotification(request);

        assertThat(notificationId).isEqualTo(savedLog.getId());
        verify(resendEmailClient).sendEmail(any(ResendEmailRequest.class));
        verify(notificationLogRepository).save(any(NotificationLog.class));
    }

    @Test
    void dispatchNotification_shouldReturnExistingIdForDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID existingNotificationId = UUID.randomUUID();

        NotificationLog existingLog = new NotificationLog();
        existingLog.setId(existingNotificationId);
        existingLog.setEventId(eventId);

        NotificationDispatchRequest request =
                NotificationDispatchRequest.builder()
                        .eventId(eventId)
                        .recipientEmail("user@example.com")
                        .notificationType(NotificationType.ACCOUNT_VERIFICATION)
                        .locale("vi-VN")
                        .parameters(new HashMap<>())
                        .build();

        when(notificationLogRepository.findByEventId(eventId)).thenReturn(Optional.of(existingLog));

        UUID notificationId = notificationService.dispatchNotification(request);

        assertThat(notificationId).isEqualTo(existingNotificationId);
    }

    @Test
    void getNotificationStatus_shouldReturnStatus() {
        UUID notificationId = UUID.randomUUID();
        NotificationLog log = new NotificationLog();
        log.setId(notificationId);
        log.setStatus(NotificationStatus.SENT);

        when(notificationLogRepository.findById(notificationId)).thenReturn(Optional.of(log));

        NotificationStatusResponse status =
                notificationService.getNotificationStatus(notificationId);

        assertThat(status).isNotNull();
        assertThat(status.getId()).isEqualTo(notificationId);
        assertThat(status.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void getNotificationStatus_shouldThrowExceptionWhenNotFound() {
        UUID notificationId = UUID.randomUUID();

        when(notificationLogRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNotificationStatus(notificationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy thông báo");
    }
}
