package com.shareride.notification.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.outbox.OutboxMessage;
import com.shareride.common.outbox.OutboxStatus;
import com.shareride.notification.dto.NotificationDispatchEvent;
import com.shareride.notification.repository.OutboxMessageRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationEventPublisherTest {

    @Mock private OutboxMessageRepository outboxRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ObjectMapper objectMapper;

    private NotificationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationEventPublisher(outboxRepository, rabbitTemplate, objectMapper);
    }

    @Test
    @DisplayName("publishViaOutbox should serialize event and save outbox message")
    void testPublishViaOutbox_success() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationDispatchEvent event =
                NotificationDispatchEvent.builder()
                        .eventId(eventId)
                        .recipientEmail("test@example.com")
                        .notificationType("ACCOUNT_VERIFICATION")
                        .locale("vi-VN")
                        .parameters(Map.of("code", "123456"))
                        .build();

        when(objectMapper.writeValueAsString(event))
                .thenReturn("{\"eventId\":\"" + eventId + "\"}");

        publisher.publishViaOutbox(event);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());

        OutboxMessage saved = captor.getValue();
        assertEquals("Notification", saved.getAggregateType());
        assertEquals(eventId.toString(), saved.getAggregateId());
        assertEquals("NotificationDispatchEvent", saved.getEventType());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
        assertNotNull(saved.getCreatedAt());
        assertEquals("{\"eventId\":\"" + eventId + "\"}", saved.getPayload());
    }

    @Test
    @DisplayName("publishViaOutbox should throw RuntimeException when serialization fails")
    void testPublishViaOutbox_jsonException() throws Exception {
        NotificationDispatchEvent event =
                NotificationDispatchEvent.builder()
                        .eventId(UUID.randomUUID())
                        .recipientEmail("test@example.com")
                        .notificationType("ACCOUNT_VERIFICATION")
                        .build();

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        assertThrows(RuntimeException.class, () -> publisher.publishViaOutbox(event));
    }

    @Test
    @DisplayName("publishDirect should send event directly to RabbitMQ")
    void testPublishDirect_success() {
        NotificationDispatchEvent event =
                NotificationDispatchEvent.builder()
                        .eventId(UUID.randomUUID())
                        .recipientEmail("test@example.com")
                        .notificationType("PASSWORD_RESET")
                        .build();

        publisher.publishDirect(event);

        verify(rabbitTemplate)
                .convertAndSend(
                        "shareride.notification.exchange", "notification.email.dispatch", event);
    }

    @Test
    @DisplayName("publishDirect should throw RuntimeException when rabbitTemplate throws")
    void testPublishDirect_exception() {
        NotificationDispatchEvent event =
                NotificationDispatchEvent.builder()
                        .eventId(UUID.randomUUID())
                        .recipientEmail("test@example.com")
                        .notificationType("PASSWORD_RESET")
                        .build();

        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThrows(RuntimeException.class, () -> publisher.publishDirect(event));
    }
}
