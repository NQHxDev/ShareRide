package com.shareride.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareride.notification.domain.NotificationChannel;
import com.shareride.notification.domain.NotificationLog;
import com.shareride.notification.domain.NotificationStatus;
import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.repository.NotificationLogRepository;
import com.shareride.notification.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NotificationLogRepository notificationLogRepository;

    @Test
    void shouldPersistNotificationLogWithAllFields() {
        UUID eventId = UUID.randomUUID();
        NotificationLog log = new NotificationLog();
        log.setEventId(eventId);
        log.setRecipientEmail("test@example.com");
        log.setNotificationType(NotificationType.ACCOUNT_VERIFICATION);
        log.setChannel(NotificationChannel.EMAIL);
        log.setLocale("vi");
        log.setSubject("Xác thực tài khoản");
        log.setStatus(NotificationStatus.PENDING);
        log.setRetryCount(0);
        log.setMaskedContextData("{\"userName\":\"Test User\",\"verificationCode\":\"***\"}");

        NotificationLog saved = notificationLogRepository.save(log);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getRecipientEmail()).isEqualTo("test@example.com");
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.ACCOUNT_VERIFICATION);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getLocale()).isEqualTo("vi");
        assertThat(saved.getSubject()).isEqualTo("Xác thực tài khoản");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getVersion()).isEqualTo(0L);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldEnforceUniqueEventIdConstraint() {
        UUID eventId = UUID.randomUUID();

        NotificationLog log1 = new NotificationLog();
        log1.setEventId(eventId);
        log1.setRecipientEmail("user1@example.com");
        log1.setNotificationType(NotificationType.ACCOUNT_VERIFICATION);
        log1.setChannel(NotificationChannel.EMAIL);
        log1.setLocale("vi");
        log1.setSubject("Subject 1");
        log1.setStatus(NotificationStatus.PENDING);
        log1.setRetryCount(0);

        notificationLogRepository.save(log1);

        NotificationLog log2 = new NotificationLog();
        log2.setEventId(eventId);
        log2.setRecipientEmail("user2@example.com");
        log2.setNotificationType(NotificationType.PASSWORD_RESET);
        log2.setChannel(NotificationChannel.EMAIL);
        log2.setLocale("en");
        log2.setSubject("Subject 2");
        log2.setStatus(NotificationStatus.PENDING);
        log2.setRetryCount(0);

        assertThatThrownBy(() -> notificationLogRepository.saveAndFlush(log2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldFindByEventId() {
        UUID eventId = UUID.randomUUID();
        NotificationLog log = new NotificationLog();
        log.setEventId(eventId);
        log.setRecipientEmail("finder@example.com");
        log.setNotificationType(NotificationType.PASSWORD_RESET);
        log.setChannel(NotificationChannel.EMAIL);
        log.setLocale("en");
        log.setSubject("Password Reset");
        log.setStatus(NotificationStatus.SENT);
        log.setProviderMessageId("msg_resend_12345");
        log.setSentAt(Instant.now());
        log.setRetryCount(0);

        notificationLogRepository.save(log);

        var found = notificationLogRepository.findByEventId(eventId);

        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(eventId);
        assertThat(found.get().getProviderMessageId()).isEqualTo("msg_resend_12345");
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldUpdateStatusWithOptimisticLocking() {
        UUID eventId = UUID.randomUUID();
        NotificationLog log = new NotificationLog();
        log.setEventId(eventId);
        log.setRecipientEmail("optimistic@example.com");
        log.setNotificationType(NotificationType.ACCOUNT_VERIFICATION);
        log.setChannel(NotificationChannel.EMAIL);
        log.setLocale("vi");
        log.setSubject("Test Subject");
        log.setStatus(NotificationStatus.PENDING);
        log.setRetryCount(0);

        NotificationLog saved = notificationLogRepository.save(log);
        assertThat(saved.getVersion()).isEqualTo(0L);

        saved.setStatus(NotificationStatus.SENT);
        saved.setProviderMessageId("msg_sent_001");
        NotificationLog updated = notificationLogRepository.save(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.SENT);
    }
}
