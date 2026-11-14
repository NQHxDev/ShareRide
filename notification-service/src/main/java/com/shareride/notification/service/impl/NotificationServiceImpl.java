package com.shareride.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.notification.client.ResendEmailClient;
import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.client.dto.ResendEmailResponse;
import com.shareride.notification.config.ResendProperties;
import com.shareride.notification.domain.NotificationChannel;
import com.shareride.notification.domain.NotificationLog;
import com.shareride.notification.domain.NotificationStatus;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.dto.NotificationStatusResponse;
import com.shareride.notification.dto.RenderedEmail;
import com.shareride.notification.repository.NotificationLogRepository;
import com.shareride.notification.service.EmailMessageSourceService;
import com.shareride.notification.service.EmailTemplateRenderer;
import com.shareride.notification.service.NotificationService;
import com.shareride.notification.util.SensitiveDataMasker;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final EmailMessageSourceService messageSourceService;
    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public UUID dispatchNotification(NotificationDispatchRequest request) {
        log.info(
                "Dispatching notification: eventId={}, type={}, recipient={}",
                request.getEventId(),
                request.getNotificationType(),
                request.getRecipientEmail());

        Locale locale = messageSourceService.resolveLocale(request.getLocale());

        RenderedEmail renderedEmail =
                emailTemplateRenderer.render(
                        request.getNotificationType(), locale, request.getParameters());

        String subject = messageSourceService.getMessage(renderedEmail.getSubject(), locale);

        ResendEmailRequest resendRequest =
                ResendEmailRequest.builder()
                        .from(resendProperties.getFromEmail())
                        .to(List.of(request.getRecipientEmail()))
                        .subject(subject)
                        .html(renderedEmail.getHtmlBody())
                        .text(renderedEmail.getPlainTextBody())
                        .build();

        ResendEmailResponse resendResponse = resendEmailClient.sendEmail(resendRequest);

        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setEventId(request.getEventId());
        notificationLog.setRecipientEmail(request.getRecipientEmail());
        notificationLog.setNotificationType(request.getNotificationType());
        notificationLog.setChannel(NotificationChannel.EMAIL);
        notificationLog.setLocale(locale.getLanguage());
        notificationLog.setSubject(subject);
        notificationLog.setStatus(NotificationStatus.SENT);
        notificationLog.setProviderMessageId(resendResponse.getId());
        notificationLog.setRetryCount(0);
        notificationLog.setSentAt(Instant.now());

        Map<String, Object> maskedParams =
                SensitiveDataMasker.maskSensitiveData(request.getParameters());
        try {
            notificationLog.setMaskedContextData(objectMapper.writeValueAsString(maskedParams));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize parameters", e);
        }

        NotificationLog saved = notificationLogRepository.save(notificationLog);

        log.info("Notification dispatched successfully: id={}", saved.getId());
        return saved.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationStatusResponse getNotificationStatus(UUID notificationId) {
        NotificationLog log =
                notificationLogRepository
                        .findById(notificationId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Notification not found: " + notificationId));

        return NotificationStatusResponse.builder()
                .id(log.getId())
                .eventId(log.getEventId())
                .recipientEmail(log.getRecipientEmail())
                .status(log.getStatus())
                .providerMessageId(log.getProviderMessageId())
                .retryCount(log.getRetryCount())
                .lastErrorMessage(log.getLastErrorMessage())
                .sentAt(log.getSentAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
