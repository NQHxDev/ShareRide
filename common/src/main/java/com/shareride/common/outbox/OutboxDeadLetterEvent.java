package com.shareride.common.outbox;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} dispatched when an outbox message is routed to the Dead Letter
 * Queue.
 */
public class OutboxDeadLetterEvent extends ApplicationEvent {

    private final OutboxMessage message;

    public OutboxDeadLetterEvent(Object source, OutboxMessage message) {
        super(source);
        this.message = message;
    }

    public OutboxMessage getMessage() {
        return message;
    }
}
