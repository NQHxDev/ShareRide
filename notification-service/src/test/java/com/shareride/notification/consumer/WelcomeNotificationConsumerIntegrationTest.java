package com.shareride.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.notification.domain.NotificationLog;
import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.dto.NotificationDispatchEvent;
import com.shareride.notification.repository.NotificationLogRepository;
import com.shareride.notification.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Integration test validating end-to-end consumption of WELCOME event from RabbitMQ queue,
 * FreeMarker template rendering, and idempotency filtering via Valkey per T026, SC-006.
 */
@SpringBootTest
@Import(TestEmailClientConfiguration.class)
@DisplayName("Integration Test: Welcome Notification Consumer")
class WelcomeNotificationConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NotificationEventConsumer consumer;

    @Autowired private NotificationLogRepository notificationLogRepository;

    @Autowired private IdempotentMessageService idempotentMessageService;

    @Autowired private StringRedisTemplate redisTemplate;

    @Autowired private ObjectMapper objectMapper;

    private static final String TEST_EMAIL = "welcome-test@shareride.vn";
    private static final String TEST_USER_NAME = "Nguyen Van Test";

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Should successfully consume and process WELCOME notification event")
    void shouldSuccessfullyProcessWelcomeEvent() throws Exception {
        UUID eventId = UUID.randomUUID();

        NotificationDispatchEvent welcomeEvent =
                NotificationDispatchEvent.builder()
                        .eventId(eventId)
                        .recipientEmail(TEST_EMAIL)
                        .notificationType("WELCOME")
                        .locale("vi-VN")
                        .parameters(Map.of("userName", TEST_USER_NAME))
                        .build();

        String eventJson = objectMapper.writeValueAsString(welcomeEvent);

        consumer.handleNotificationEvent(eventJson);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            List<NotificationLog> logs =
                                    notificationLogRepository.findByRecipientEmail(TEST_EMAIL);
                            long welcomeCount =
                                    logs.stream()
                                            .filter(
                                                    log ->
                                                            log.getNotificationType()
                                                                    == NotificationType.WELCOME)
                                            .count();
                            assertThat(welcomeCount).isEqualTo(1);
                        });

        List<NotificationLog> logs = notificationLogRepository.findByRecipientEmail(TEST_EMAIL);
        NotificationLog log =
                logs.stream()
                        .filter(l -> l.getNotificationType() == NotificationType.WELCOME)
                        .findFirst()
                        .orElseThrow();

        assertThat(log.getRecipientEmail()).isEqualTo(TEST_EMAIL);
        assertThat(log.getNotificationType()).isEqualTo(NotificationType.WELCOME);
        assertThat(log.getLocale()).isEqualTo("vi");
    }

    @Test
    @DisplayName("Should deduplicate WELCOME event using idempotency service")
    void shouldDeduplicateWelcomeEvent() throws Exception {
        UUID eventId = UUID.randomUUID();

        NotificationDispatchEvent welcomeEvent =
                NotificationDispatchEvent.builder()
                        .eventId(eventId)
                        .recipientEmail(TEST_EMAIL)
                        .notificationType("WELCOME")
                        .locale("vi-VN")
                        .parameters(Map.of("userName", TEST_USER_NAME))
                        .build();

        String eventJson = objectMapper.writeValueAsString(welcomeEvent);

        consumer.handleNotificationEvent(eventJson);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            List<NotificationLog> logs =
                                    notificationLogRepository.findByRecipientEmail(TEST_EMAIL);
                            assertThat(logs).hasSize(1);
                        });

        consumer.handleNotificationEvent(eventJson);

        Thread.sleep(1000);

        List<NotificationLog> finalLogs =
                notificationLogRepository.findByRecipientEmail(TEST_EMAIL);
        assertThat(finalLogs)
                .as("Duplicate event should not create additional log entry")
                .hasSize(1);
    }

    @Test
    @DisplayName("Should handle WELCOME event with English locale")
    void shouldHandleWelcomeEventWithEnglishLocale() throws Exception {
        UUID eventId = UUID.randomUUID();

        NotificationDispatchEvent welcomeEvent =
                NotificationDispatchEvent.builder()
                        .eventId(eventId)
                        .recipientEmail(TEST_EMAIL)
                        .notificationType("WELCOME")
                        .locale("en-US")
                        .parameters(Map.of("userName", "John Doe"))
                        .build();

        String eventJson = objectMapper.writeValueAsString(welcomeEvent);

        consumer.handleNotificationEvent(eventJson);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            List<NotificationLog> logs =
                                    notificationLogRepository.findByRecipientEmail(TEST_EMAIL);
                            assertThat(logs).isNotEmpty();
                            NotificationLog log = logs.get(0);
                            assertThat(log.getLocale()).isEqualTo("en");
                        });
    }

    @Test
    @DisplayName("Should mark event as processed in Valkey for idempotency")
    void shouldMarkEventAsProcessedInValkey() throws Exception {
        UUID eventId = UUID.randomUUID();

        NotificationDispatchEvent welcomeEvent =
                NotificationDispatchEvent.builder()
                        .eventId(eventId)
                        .recipientEmail(TEST_EMAIL)
                        .notificationType("WELCOME")
                        .locale("vi-VN")
                        .parameters(Map.of("userName", TEST_USER_NAME))
                        .build();

        String eventJson = objectMapper.writeValueAsString(welcomeEvent);

        consumer.handleNotificationEvent(eventJson);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            boolean wasProcessedBefore =
                                    !idempotentMessageService.markAsProcessed(eventId.toString());
                            assertThat(wasProcessedBefore)
                                    .as("Event should be marked as processed in Valkey")
                                    .isTrue();
                        });
    }
}
