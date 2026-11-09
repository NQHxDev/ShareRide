package com.shareride.common.outbox;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} dispatched when an outbox message is successfully relayed through
 * the default publisher.
 */
public class OutboxRelayedEvent extends ApplicationEvent {

    private final OutboxMessage message;

    public OutboxRelayedEvent(Object source, OutboxMessage message) {
        super(source);
        this.message = message;
    }

    public OutboxMessage getMessage() {
        return message;
    }
}
