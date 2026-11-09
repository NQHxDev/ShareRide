package com.shareride.common.outbox;

import com.shareride.common.metrics.ShareRideMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Default Dead Letter Queue handler for permanently failed transactional outbox messages.
 *
 * <p>Emits an error log with message metadata and trace ID, records dead letter metrics, and
 * publishes an {@link OutboxDeadLetterEvent}.
 */
@Slf4j
public class DefaultOutboxDeadLetterHandler implements OutboxDeadLetterHandler {

    private final ObjectProvider<ShareRideMetrics> metricsProvider;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DefaultOutboxDeadLetterHandler(
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ApplicationEventPublisher applicationEventPublisher) {
        this.metricsProvider = metricsProvider;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void handle(OutboxMessage message) {
        log.error(
                "CRITICAL: Outbox message routed to DEAD_LETTER [id={}, aggregateType={},"
                        + " aggregateId={}, eventType={}, retries={}, error={}]",
                message.getId(),
                message.getAggregateType(),
                message.getAggregateId(),
                message.getEventType(),
                message.getRetryCount(),
                message.getErrorMessage());

        if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
            metricsProvider
                    .getIfAvailable()
                    .incrementCounter(
                            "outbox.dead_letter.total",
                            "aggregateType",
                            message.getAggregateType(),
                            "eventType",
                            message.getEventType());
        }

        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new OutboxDeadLetterEvent(this, message));
        }
    }
}
