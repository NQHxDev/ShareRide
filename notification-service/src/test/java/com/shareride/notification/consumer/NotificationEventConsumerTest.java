package com.shareride.notification.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.notification.dto.NotificationDispatchEvent;
import com.shareride.notification.dto.NotificationDispatchRequest;
import com.shareride.notification.service.NotificationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test for NotificationEventConsumer verifying idempotent deduplication logic. */
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private IdempotentMessageService idempotentMessageService;

    @Mock private NotificationService notificationService;

    @Mock private ObjectMapper objectMapper;

    @InjectMocks private NotificationEventConsumer consumer;

    private NotificationDispatchEvent sampleEvent;
    private String sampleEventJson;

    @BeforeEach
    void setUp() throws Exception {
        UUID eventId = UUID.randomUUID();
        sampleEvent =
                NotificationDispatchEvent.builder()
                        .eventId(eventId)
                        .recipientEmail("test@example.com")
                        .notificationType("ACCOUNT_VERIFICATION")
                        .locale("vi-VN")
                        .parameters(
                                Map.of(
                                        "userName", "Test User",
                                        "verificationCode", "123456",
                                        "expiresInMinutes", 15))
                        .build();

        sampleEventJson =
                "{\"eventId\":\"" + eventId + "\",\"recipientEmail\":\"test@example.com\"}";
    }

    @Test
    void shouldProcessNewEvent() throws Exception {
        // Given: Event is not yet processed
        when(idempotentMessageService.markAsProcessed(sampleEvent.getEventId().toString()))
                .thenReturn(true);
        when(objectMapper.readValue(anyString(), eq(NotificationDispatchEvent.class)))
                .thenReturn(sampleEvent);

        // When: Consumer receives event
        consumer.handleNotificationEvent(sampleEventJson);

        // Then: Event is marked as processed and notification is dispatched
        verify(idempotentMessageService, times(1))
                .markAsProcessed(sampleEvent.getEventId().toString());
        verify(notificationService, times(1))
                .dispatchNotification(any(NotificationDispatchRequest.class));
    }

    @Test
    void shouldSkipDuplicateEvent() throws Exception {
        // Given: Event is already processed (duplicate)
        when(idempotentMessageService.markAsProcessed(sampleEvent.getEventId().toString()))
                .thenReturn(false);
        when(objectMapper.readValue(anyString(), eq(NotificationDispatchEvent.class)))
                .thenReturn(sampleEvent);

        // When: Consumer receives duplicate event
        consumer.handleNotificationEvent(sampleEventJson);

        // Then: Idempotency check is performed, but notification service is NOT called
        verify(idempotentMessageService, times(1))
                .markAsProcessed(sampleEvent.getEventId().toString());
        verify(notificationService, never()).dispatchNotification(any());
    }

    @Test
    void shouldLogErrorAndSkipOnMalformedJson() throws Exception {
        // Given: Malformed JSON payload
        String malformedJson = "{invalid-json}";
        when(objectMapper.readValue(anyString(), eq(NotificationDispatchEvent.class)))
                .thenThrow(new RuntimeException("JSON parse error"));

        // When: Consumer receives malformed event
        try {
            consumer.handleNotificationEvent(malformedJson);
        } catch (RuntimeException e) {
            // Expected to be thrown and re-routed to DLQ
        }

        // Then: Idempotency is NOT checked and notification service is NOT called
        verify(idempotentMessageService, never()).markAsProcessed(anyString());
        verify(notificationService, never()).dispatchNotification(any());
    }
}
