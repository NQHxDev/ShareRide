package com.shareride.common.outbox;

import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.metrics.ShareRideMetrics;
import com.shareride.common.telemetry.MessagingContextPropagator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise relay service implementing the polling publisher pattern for the Transactional Outbox.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Batch processing of pending and failed domain events
 *   <li>Exponential backoff and retry mechanism for transient messaging failures
 *   <li>Dead Letter Queue (DLQ) routing upon exceeding maximum configured retries
 *   <li>Distributed context propagation (Trace ID, MDC) across message publication
 *   <li>Operational DLQ inspection and re-queueing capabilities
 * </ul>
 */
@Slf4j
@Service
public class OutboxRelayService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxDeadLetterHandler deadLetterHandler;
    private final OutboxProperties outboxProperties;
    private final ObjectProvider<ShareRideMetrics> metricsProvider;

    public OutboxRelayService(
            OutboxMessageRepository outboxMessageRepository,
            OutboxPublisher outboxPublisher,
            OutboxDeadLetterHandler deadLetterHandler,
            OutboxProperties outboxProperties,
            ObjectProvider<ShareRideMetrics> metricsProvider) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.outboxPublisher = outboxPublisher;
        this.deadLetterHandler = deadLetterHandler;
        this.outboxProperties = outboxProperties;
        this.metricsProvider = metricsProvider;
    }

    /**
     * Executes a batch relay run, polling pending and retry-eligible messages from the database and
     * publishing them through the configured {@link OutboxPublisher}.
     *
     * @return summary result of the batch run
     */
    public RelayBatchResult processBatch() {
        int batchSize = outboxProperties.getBatchSize();
        List<OutboxStatus> candidateStatuses = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
        Pageable pageable = PageRequest.of(0, batchSize);

        List<OutboxMessage> messages =
                outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(
                        candidateStatuses, pageable);

        if (messages.isEmpty()) {
            return RelayBatchResult.empty();
        }

        int processed = 0;
        int failed = 0;
        int deadLettered = 0;
        int skipped = 0;

        for (OutboxMessage message : messages) {
            if (message.getStatus() == OutboxStatus.FAILED) {
                if (message.getRetryCount() >= outboxProperties.getMaxRetries()) {
                    routeToDeadLetter(
                            message,
                            "Exceeded maximum allowed retries ["
                                    + outboxProperties.getMaxRetries()
                                    + "]");
                    deadLettered++;
                    continue;
                }

                long backoffDelay = calculateBackoff(message.getRetryCount());
                Instant lastAttempt = message.getProcessedAt();
                if (lastAttempt != null
                        && Instant.now().isBefore(lastAttempt.plusMillis(backoffDelay))) {
                    skipped++;
                    continue;
                }
            }

            try {
                setupTracingContext(message);
                outboxPublisher.publish(message);
                message.markProcessed();
                outboxMessageRepository.save(message);
                recordPublishedMetric(message);
                processed++;
                log.info(
                        "Successfully relayed outbox event [id={}, aggregateType={},"
                                + " aggregateId={}, eventType={}]",
                        message.getId(),
                        message.getAggregateType(),
                        message.getAggregateId(),
                        message.getEventType());
            } catch (Exception ex) {
                log.warn(
                        "Failed to relay outbox event [id={}, aggregateType={}, aggregateId={},"
                                + " eventType={}, retryCount={}]: {}",
                        message.getId(),
                        message.getAggregateType(),
                        message.getAggregateId(),
                        message.getEventType(),
                        message.getRetryCount(),
                        ex.getMessage());

                int nextRetryCount = message.getRetryCount() + 1;
                if (nextRetryCount >= outboxProperties.getMaxRetries()) {
                    routeToDeadLetter(
                            message,
                            "Exceeded maximum retries ["
                                    + outboxProperties.getMaxRetries()
                                    + "]: "
                                    + ex.getMessage());
                    deadLettered++;
                } else {
                    message.markFailed(ex.getMessage());
                    outboxMessageRepository.save(message);
                    recordRetryMetric(message);
                    failed++;
                }
            } finally {
                MessagingContextPropagator.clear();
            }
        }

        return RelayBatchResult.builder()
                .totalFetched(messages.size())
                .processed(processed)
                .failed(failed)
                .deadLettered(deadLettered)
                .skipped(skipped)
                .build();
    }

    private void routeToDeadLetter(OutboxMessage message, String reason) {
        message.markDeadLetter(reason);
        outboxMessageRepository.save(message);
        if (deadLetterHandler != null) {
            deadLetterHandler.handle(message);
        }
    }

    private long calculateBackoff(int retryCount) {
        long baseBackoff = outboxProperties.getRetryBackoffMs();
        double multiplier = outboxProperties.getBackoffMultiplier();
        long maxBackoff = outboxProperties.getMaxBackoffMs();
        long calculated = (long) (baseBackoff * Math.pow(multiplier, Math.max(0, retryCount - 1)));
        return Math.min(calculated, maxBackoff);
    }

    private void setupTracingContext(OutboxMessage message) {
        String traceId = message.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            TraceIdContext.setTraceId(traceId);
            MDC.put(MdcConstants.TRACE_ID, traceId);
        }
    }

    private void recordPublishedMetric(OutboxMessage message) {
        if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
            metricsProvider
                    .getIfAvailable()
                    .incrementCounter(
                            "outbox.relayed.total",
                            "aggregateType",
                            message.getAggregateType(),
                            "eventType",
                            message.getEventType());
        }
    }

    private void recordRetryMetric(OutboxMessage message) {
        if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
            metricsProvider
                    .getIfAvailable()
                    .incrementCounter(
                            "outbox.retries.total",
                            "aggregateType",
                            message.getAggregateType(),
                            "eventType",
                            message.getEventType());
        }
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> getDeadLetterMessages(Pageable pageable) {
        return outboxMessageRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.DEAD_LETTER, pageable);
    }

    @Transactional
    public int requeueDeadLetter(UUID messageId) {
        int updated = outboxMessageRepository.resetStatusById(messageId, OutboxStatus.PENDING);
        if (updated > 0) {
            log.info("Re-queued dead letter outbox message [id={}]", messageId);
        }
        return updated;
    }

    @Transactional
    public int requeueAllDeadLetters() {
        int updated =
                outboxMessageRepository.resetStatusByTargetStatus(
                        OutboxStatus.DEAD_LETTER, OutboxStatus.PENDING);
        log.info("Re-queued all dead letter outbox messages: total={}", updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public long getDeadLetterCount() {
        return outboxMessageRepository.countByStatus(OutboxStatus.DEAD_LETTER);
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return outboxMessageRepository.countByStatus(OutboxStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long getProcessedCount() {
        return outboxMessageRepository.countByStatus(OutboxStatus.PROCESSED);
    }
}
