package com.shareride.auth.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.outbox.OutboxMessage;
import com.shareride.common.outbox.OutboxService;
import com.shareride.common.outbox.OutboxStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock private OutboxService outboxService;

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private AuthEventPublisher authEventPublisher;

    @Test
    void publishAccountEvent_ShouldRecordEventInOutboxWithCorrectSchema() {
        final String accountId = UUID.randomUUID().toString();
        final String eventType = "ACCOUNT_VERIFICATION";

        // Create payload matching AccountVerificationEvent.json schema
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userName", "Nguyen Van A");
        parameters.put("verificationCode", "K8M2P4");
        parameters.put("expiresInMinutes", 15);

        final Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("recipientEmail", "test@example.com");
        payload.put("notificationType", "ACCOUNT_VERIFICATION");
        payload.put("locale", "vi-VN");
        payload.put("parameters", parameters);

        final OutboxMessage outboxMessage =
                OutboxMessage.builder()
                        .aggregateType("Account")
                        .aggregateId(accountId)
                        .eventType(eventType)
                        .status(OutboxStatus.PENDING)
                        .build();

        when(outboxService.recordEvent(eq("Account"), eq(accountId), eq(eventType), any()))
                .thenReturn(outboxMessage);

        authEventPublisher.publishAccountEvent(accountId, eventType, payload);

        verify(outboxService, times(1))
                .recordEvent(
                        eq("Account"),
                        eq(accountId),
                        eq(eventType),
                        argThat(
                                p -> {
                                    if (!(p instanceof Map<?, ?> map)) {
                                        return false;
                                    }
                                    // Assert required fields per
                                    // contracts/AccountVerificationEvent.json
                                    return map.containsKey("eventId")
                                            && map.containsKey("recipientEmail")
                                            && map.containsKey("notificationType")
                                            && map.containsKey("locale")
                                            && map.containsKey("parameters")
                                            && "ACCOUNT_VERIFICATION"
                                                    .equals(map.get("notificationType"))
                                            && ("vi-VN".equals(map.get("locale"))
                                                    || "en-US".equals(map.get("locale")));
                                }));
    }

    @Test
    void publishAccountEvent_ShouldValidateNestedParametersStructure() {
        final String accountId = UUID.randomUUID().toString();
        final String eventType = "ACCOUNT_VERIFICATION";

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("userName", "Test User");
        parameters.put("verificationCode", "ABC234");
        parameters.put("expiresInMinutes", 15);

        final Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("recipientEmail", "user@example.com");
        payload.put("notificationType", "ACCOUNT_VERIFICATION");
        payload.put("locale", "vi-VN");
        payload.put("parameters", parameters);

        final OutboxMessage outboxMessage =
                OutboxMessage.builder()
                        .aggregateType("Account")
                        .aggregateId(accountId)
                        .eventType(eventType)
                        .status(OutboxStatus.PENDING)
                        .build();

        when(outboxService.recordEvent(anyString(), anyString(), anyString(), any()))
                .thenReturn(outboxMessage);

        authEventPublisher.publishAccountEvent(accountId, eventType, payload);

        verify(outboxService, times(1))
                .recordEvent(
                        eq("Account"),
                        eq(accountId),
                        eq(eventType),
                        argThat(
                                p -> {
                                    if (!(p instanceof Map<?, ?> map)) {
                                        return false;
                                    }
                                    if (!(map.get("parameters") instanceof Map<?, ?> params)) {
                                        return false;
                                    }
                                    // Assert nested parameters per schema
                                    return params.containsKey("userName")
                                            && params.containsKey("verificationCode")
                                            && params.containsKey("expiresInMinutes")
                                            && params.get("verificationCode") instanceof String code
                                            && code.length() == 6;
                                }));
    }

    @Test
    void publishDirect_ShouldSendEventToRabbitMQ() {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("recipientEmail", "test@example.com");

        authEventPublisher.publishDirect(payload);

        verify(rabbitTemplate, times(1))
                .convertAndSend(
                        eq("shareride.notification.exchange"),
                        eq("notification.email.dispatch"),
                        eq(payload));
    }

    @Test
    void publishDirect_ShouldThrowExceptionWhenRabbitMQFails() {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());

        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq("shareride.notification.exchange"),
                        eq("notification.email.dispatch"),
                        any(Map.class));

        final RuntimeException exception =
                assertThrows(
                        RuntimeException.class, () -> authEventPublisher.publishDirect(payload));

        assertEquals("Direct event publishing failed", exception.getMessage());
        assertNotNull(exception.getCause());
    }
}
