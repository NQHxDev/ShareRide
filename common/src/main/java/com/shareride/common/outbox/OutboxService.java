package com.shareride.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.filter.TraceIdContext;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise service managing the Transactional Outbox.
 *
 * <p>Ensures that domain events are safely written into the outbox table in the same local ACID
 * transaction as business mutations, preventing dual-write inconsistencies, and exposes operational
 * relay and Dead Letter Queue management operations.
 */
@Slf4j
@Service
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final OutboxRelayService outboxRelayService;

    @Autowired
    public OutboxService(
            OutboxMessageRepository outboxMessageRepository,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<OutboxRelayService> outboxRelayServiceProvider) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper =
                (objectMapperProvider != null && objectMapperProvider.getIfAvailable() != null)
                        ? objectMapperProvider.getIfAvailable()
                        : new ObjectMapper();
        this.outboxRelayService =
                (outboxRelayServiceProvider != null)
                        ? outboxRelayServiceProvider.getIfAvailable()
                        : null;
    }

    public OutboxService(
            OutboxMessageRepository outboxMessageRepository,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this(outboxMessageRepository, objectMapperProvider, null);
    }

    public OutboxService(
            OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper) {
        this(outboxMessageRepository, objectMapper, null);
    }

    public OutboxService(
            OutboxMessageRepository outboxMessageRepository,
            ObjectMapper objectMapper,
            OutboxRelayService outboxRelayService) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.outboxRelayService = outboxRelayService;
    }

    /**
     * Enqueues a domain event into the Outbox within an already active transaction. Propagation
     * MANDATORY guarantees the caller is inside an active transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage recordEvent(
            String aggregateType, String aggregateId, String eventType, Object payload) {
        return createAndSaveMessage(aggregateType, aggregateId, eventType, payload);
    }

    /** Enqueues a domain event into the Outbox with standard transaction support. */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxMessage publishEvent(
            String aggregateType, String aggregateId, String eventType, Object payload) {
        return createAndSaveMessage(aggregateType, aggregateId, eventType, payload);
    }

    /**
     * Manually triggers a batch relay iteration of pending outbox messages.
     *
     * @return batch execution result
     */
    public RelayBatchResult relayPendingMessages() {
        if (outboxRelayService != null) {
            return outboxRelayService.processBatch();
        }
        log.warn("OutboxRelayService is not available in the current context");
        return RelayBatchResult.empty();
    }

    /**
     * Re-queues a single dead letter message back to PENDING.
     *
     * @param messageId unique message ID
     * @return number of records updated
     */
    public int requeueDeadLetter(UUID messageId) {
        if (outboxRelayService != null) {
            return outboxRelayService.requeueDeadLetter(messageId);
        }
        return outboxMessageRepository.resetStatusById(messageId, OutboxStatus.PENDING);
    }

    /**
     * Re-queues all dead letter messages back to PENDING.
     *
     * @return total records re-queued
     */
    public int requeueAllDeadLetters() {
        if (outboxRelayService != null) {
            return outboxRelayService.requeueAllDeadLetters();
        }
        return outboxMessageRepository.resetStatusByTargetStatus(
                OutboxStatus.DEAD_LETTER, OutboxStatus.PENDING);
    }

    /** Retrieves dead letter messages with pagination. */
    @Transactional(readOnly = true)
    public List<OutboxMessage> getDeadLetterMessages(Pageable pageable) {
        return outboxMessageRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.DEAD_LETTER, pageable);
    }

    /** Returns the total count of messages currently in DEAD_LETTER status. */
    @Transactional(readOnly = true)
    public long getDeadLetterCount() {
        return outboxMessageRepository.countByStatus(OutboxStatus.DEAD_LETTER);
    }

    /** Returns the total count of messages currently in PENDING status. */
    @Transactional(readOnly = true)
    public long getPendingCount() {
        return outboxMessageRepository.countByStatus(OutboxStatus.PENDING);
    }

    private OutboxMessage createAndSaveMessage(
            String aggregateType, String aggregateId, String eventType, Object payload) {
        String jsonPayload;
        try {
            jsonPayload =
                    payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to serialize outbox event payload for {} {}",
                    aggregateType,
                    aggregateId,
                    e);
            throw new IllegalArgumentException("Cannot serialize event payload to JSON", e);
        }

        String traceId = TraceIdContext.getTraceId();

        OutboxMessage outboxMessage =
                OutboxMessage.builder()
                        .aggregateType(aggregateType)
                        .aggregateId(aggregateId)
                        .eventType(eventType)
                        .payload(jsonPayload)
                        .traceId(traceId)
                        .status(OutboxStatus.PENDING)
                        .build();

        OutboxMessage saved = outboxMessageRepository.save(outboxMessage);
        log.info(
                "Recorded Outbox event [aggregateType={}, aggregateId={}, eventType={}] with"
                        + " traceId={}",
                aggregateType,
                aggregateId,
                eventType,
                traceId);
        return saved;
    }
}
