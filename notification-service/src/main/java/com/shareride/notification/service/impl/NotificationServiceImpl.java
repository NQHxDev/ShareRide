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
import com.shareride.notification.exception.UnrecoverableDeliveryException;
import com.shareride.notification.repository.NotificationLogRepository;
import com.shareride.notification.service.EmailMessageSourceService;
import com.shareride.notification.service.EmailTemplateRenderer;
import com.shareride.notification.service.NotificationService;
import com.shareride.notification.util.SensitiveDataMasker;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

        // Idempotency check: prevent duplicate email dispatches
        Optional<NotificationLog> existing =
                notificationLogRepository.findByEventId(request.getEventId());

        if (existing.isPresent()) {
            NotificationLog existingLog = existing.get();
            log.info(
                    "Duplicate notification detected for eventId={}. Returning existing"
                            + " notification ID={}",
                    request.getEventId(),
                    existingLog.getId());
            return existingLog.getId();
        }

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

        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setEventId(request.getEventId());
        notificationLog.setRecipientEmail(request.getRecipientEmail());
        notificationLog.setNotificationType(request.getNotificationType());
        notificationLog.setChannel(NotificationChannel.EMAIL);
        notificationLog.setLocale(locale.getLanguage());
        notificationLog.setSubject(subject);
        notificationLog.setRetryCount(0);

        Map<String, Object> maskedParams =
                SensitiveDataMasker.maskSensitiveData(request.getParameters());
        try {
            notificationLog.setMaskedContextData(objectMapper.writeValueAsString(maskedParams));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize parameters", e);
        }

        try {
            ResendEmailResponse resendResponse = resendEmailClient.sendEmail(resendRequest);

            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setProviderMessageId(resendResponse.getId());
            notificationLog.setSentAt(Instant.now());

            log.info("Email sent successfully. Provider message ID: {}", resendResponse.getId());

        } catch (Exception e) {
            log.error("Failed to send email via Resend after retries: {}", e.getMessage(), e);

            notificationLog.setStatus(NotificationStatus.PERMANENTLY_FAILED);
            notificationLog.setLastErrorMessage(e.getMessage());
            notificationLog.setRetryCount(3); // All retry attempts exhausted

            // Persist failure state before propagating exception
            notificationLogRepository.save(notificationLog);

            // Propagate exception to trigger RabbitMQ DLQ routing
            throw new UnrecoverableDeliveryException(
                    "Gửi email thất bại sau khi thử lại: " + e.getMessage(), e);
        }

        NotificationLog saved = notificationLogRepository.save(notificationLog);

        log.info("Notification log persisted: id={}, status={}", saved.getId(), saved.getStatus());
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
                                                "Không tìm thấy thông báo: " + notificationId));

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
