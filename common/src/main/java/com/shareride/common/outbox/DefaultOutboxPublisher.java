package com.shareride.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Default fallback implementation of {@link OutboxPublisher}.
 *
 * <p>Logs the relayed event and dispatches an {@link OutboxRelayedEvent} via Spring's {@link
 * ApplicationEventPublisher} so in-process listeners can react to domain events.
 */
@Slf4j
public class DefaultOutboxPublisher implements OutboxPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public DefaultOutboxPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(OutboxMessage message) throws Exception {
        log.info(
                "Relaying Outbox event [id={}, aggregateType={}, aggregateId={}, eventType={}]",
                message.getId(),
                message.getAggregateType(),
                message.getAggregateId(),
                message.getEventType());

        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new OutboxRelayedEvent(this, message));
        }
    }
}
