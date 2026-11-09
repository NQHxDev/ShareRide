package com.shareride.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.communication.rabbitmq.consumer.IdempotentMessageService;
import com.shareride.common.communication.rabbitmq.event.ShareRideEvent;
import com.shareride.common.communication.rabbitmq.publisher.RabbitMqEventPublisher;
import com.shareride.common.outbox.OutboxService;
import com.shareride.template.constant.TemplateConstants;
import com.shareride.template.consumer.TemplateEventConsumer;
import com.shareride.template.domain.TemplateItem;
import com.shareride.template.domain.TemplateItemStatus;
import com.shareride.template.publisher.TemplateEventPublisher;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateMessagingTest {

    @Mock private OutboxService outboxService;

    @Mock private RabbitMqEventPublisher rabbitMqEventPublisher;

    @Mock private IdempotentMessageService idempotentMessageService;

    @Test
    @DisplayName("Publisher should record event in outbox and publish direct async event")
    void testPublisher() {
        TemplateEventPublisher publisher =
                new TemplateEventPublisher(outboxService, rabbitMqEventPublisher);

        TemplateItem item =
                TemplateItem.builder()
                        .name("Promo")
                        .code("PROMO_2026")
                        .status(TemplateItemStatus.ACTIVE)
                        .build();
        item.setId(UUID.randomUUID());

        publisher.recordItemCreatedOutbox(item);
        verify(outboxService)
                .recordEvent(
                        eq("TEMPLATE_ITEM"),
                        eq(item.getId().toString()),
                        eq("TEMPLATE_ITEM_CREATED"),
                        any());

        publisher.publishItemCreatedDirect(item);
        verify(rabbitMqEventPublisher)
                .publishAsync(
                        eq(TemplateConstants.RabbitMq.EXCHANGE_EVENTS),
                        eq(TemplateConstants.RabbitMq.ROUTING_KEY_ITEM_CREATED),
                        any());
    }

    @Test
    @DisplayName("Consumer should skip execution if message is duplicate")
    void testConsumerDuplicateSkipped() {
        TemplateEventConsumer consumer = new TemplateEventConsumer(idempotentMessageService);

        ShareRideEvent<Map<String, Object>> event =
                ShareRideEvent.create(
                        "template.item.created",
                        "TEMPLATE_ITEM",
                        "123",
                        "test",
                        Map.of("code", "TEST"));

        when(idempotentMessageService.isProcessed(event.getEventId())).thenReturn(true);

        consumer.handleTemplateEvent(event);

        verify(idempotentMessageService, never()).markAsProcessed(any(), any());
    }

    @Test
    @DisplayName("Consumer should process and mark message processed when not duplicate")
    void testConsumerNewMessageProcessed() {
        TemplateEventConsumer consumer = new TemplateEventConsumer(idempotentMessageService);

        ShareRideEvent<Map<String, Object>> event =
                ShareRideEvent.create(
                        "template.item.created",
                        "TEMPLATE_ITEM",
                        "123",
                        "test",
                        Map.of("code", "TEST"));

        when(idempotentMessageService.isProcessed(event.getEventId())).thenReturn(false);

        consumer.handleTemplateEvent(event);

        verify(idempotentMessageService).markAsProcessed(eq(event.getEventId()), any());
    }
}
