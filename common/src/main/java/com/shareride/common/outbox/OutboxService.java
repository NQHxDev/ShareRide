package com.shareride.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.filter.TraceIdContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise service managing the Transactional Outbox.
 * <p>
 * Ensures that domain events are safely written into the outbox table in the
 * same local ACID transaction as business mutations, preventing dual-write inconsistencies.
 */
@Slf4j
@Service
public class OutboxService {

   private final OutboxMessageRepository outboxMessageRepository;
   private final ObjectMapper objectMapper;

   @Autowired
   public OutboxService(OutboxMessageRepository outboxMessageRepository, ObjectProvider<ObjectMapper> objectMapperProvider) {
      this.outboxMessageRepository = outboxMessageRepository;
      this.objectMapper = (objectMapperProvider != null && objectMapperProvider.getIfAvailable() != null)
            ? objectMapperProvider.getIfAvailable()
            : new ObjectMapper();
   }

   public OutboxService(OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper) {
      this.outboxMessageRepository = outboxMessageRepository;
      this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
   }

   /**
    * Enqueues a domain event into the Outbox within an already active transaction.
    * Propagation MANDATORY guarantees the caller is inside an active transaction.
    */
   @Transactional(propagation = Propagation.MANDATORY)
   public OutboxMessage recordEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
      return createAndSaveMessage(aggregateType, aggregateId, eventType, payload);
   }

   /**
    * Enqueues a domain event into the Outbox with standard transaction support.
    */
   @Transactional(propagation = Propagation.REQUIRED)
   public OutboxMessage publishEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
      return createAndSaveMessage(aggregateType, aggregateId, eventType, payload);
   }

   private OutboxMessage createAndSaveMessage(String aggregateType, String aggregateId, String eventType,
         Object payload) {
      String jsonPayload;
      try {
         jsonPayload = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
      } catch (JsonProcessingException e) {
         log.error("Failed to serialize outbox event payload for {} {}", aggregateType, aggregateId, e);
         throw new IllegalArgumentException("Cannot serialize event payload to JSON", e);
      }

      String traceId = TraceIdContext.getTraceId();

      OutboxMessage outboxMessage = OutboxMessage.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .payload(jsonPayload)
            .traceId(traceId)
            .status(OutboxStatus.PENDING)
            .build();

      OutboxMessage saved = outboxMessageRepository.save(outboxMessage);
      log.info("Recorded Outbox event [aggregateType={}, aggregateId={}, eventType={}] with traceId={}",
            aggregateType, aggregateId, eventType, traceId);
      return saved;
   }

}
