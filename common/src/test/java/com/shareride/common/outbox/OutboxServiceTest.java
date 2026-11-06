package com.shareride.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.filter.TraceIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

   @Mock
   private OutboxMessageRepository outboxMessageRepository;

   private OutboxService outboxService;
   private ObjectMapper objectMapper;

   @BeforeEach
   void setUp() {
      objectMapper = new ObjectMapper();
      outboxService = new OutboxService(outboxMessageRepository, objectMapper);
      TraceIdContext.setTraceId("trace-outbox-999");
   }

   @AfterEach
   void tearDown() {
      TraceIdContext.clear();
   }

   @Test
   @DisplayName("recordEvent should serialize payload, capture traceId and save PENDING OutboxMessage")
   void testRecordEvent() {
      when(outboxMessageRepository.save(any(OutboxMessage.class)))
            .thenAnswer(inv -> inv.getArgument(0));

      Map<String, Object> eventData = Map.of("userId", "u-123", "email", "john@shareride.com");
      OutboxMessage recorded = outboxService.recordEvent("User", "u-123", "USER_REGISTERED", eventData);

      assertNotNull(recorded);
      assertEquals("User", recorded.getAggregateType());
      assertEquals("u-123", recorded.getAggregateId());
      assertEquals("USER_REGISTERED", recorded.getEventType());
      assertEquals("trace-outbox-999", recorded.getTraceId());
      assertEquals(OutboxStatus.PENDING, recorded.getStatus());
      assertTrue(recorded.getPayload().contains("john@shareride.com"));

      ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
      verify(outboxMessageRepository, times(1)).save(captor.capture());
      assertEquals("USER_REGISTERED", captor.getValue().getEventType());
   }

   @Test
   @DisplayName("OutboxMessage status transitions should update timestamps and errors")
   void testOutboxStatusTransitions() {
      OutboxMessage message = OutboxMessage.builder()
            .aggregateType("Ride")
            .aggregateId("ride-456")
            .eventType("RIDE_COMPLETED")
            .payload("{}")
            .build();

      assertEquals(OutboxStatus.PENDING, message.getStatus());
      assertEquals(0, message.getRetryCount());

      message.markFailed("Kafka broker connection timeout");
      assertEquals(OutboxStatus.FAILED, message.getStatus());
      assertEquals(1, message.getRetryCount());
      assertEquals("Kafka broker connection timeout", message.getErrorMessage());

      message.markProcessed();
      assertEquals(OutboxStatus.PROCESSED, message.getStatus());
      assertNotNull(message.getProcessedAt());
   }

}
