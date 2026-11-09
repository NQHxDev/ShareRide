package com.shareride.common.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.filter.TraceIdContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock private OutboxMessageRepository outboxMessageRepository;
    @Mock private OutboxRelayService outboxRelayService;

    private OutboxService outboxService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        outboxService =
                new OutboxService(outboxMessageRepository, objectMapper, outboxRelayService);
        TraceIdContext.setTraceId("trace-outbox-999");
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
    }

    @Test
    @DisplayName(
            "recordEvent should serialize payload, capture traceId and save PENDING OutboxMessage")
    void testRecordEvent() {
        when(outboxMessageRepository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> eventData = Map.of("userId", "u-123", "email", "john@shareride.com");
        OutboxMessage recorded =
                outboxService.recordEvent("User", "u-123", "USER_REGISTERED", eventData);

        assertNotNull(recorded);
        assertEquals("User", recorded.getAggregateType());
        assertEquals("u-123", recorded.getAggregateId());
        assertEquals("USER_REGISTERED", recorded.getEventType());
        assertEquals("trace-outbox-999", recorded.getTraceId());
        assertEquals(OutboxStatus.PENDING, recorded.getStatus());
        assertTrue(recorded.isPending());
        assertTrue(recorded.getPayload().contains("john@shareride.com"));

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository, times(1)).save(captor.capture());
        assertEquals("USER_REGISTERED", captor.getValue().getEventType());
    }

    @Test
    @DisplayName("OutboxMessage status transitions should update timestamps and errors")
    void testOutboxStatusTransitions() {
        OutboxMessage message =
                OutboxMessage.builder()
                        .aggregateType("Ride")
                        .aggregateId("ride-456")
                        .eventType("RIDE_COMPLETED")
                        .payload("{}")
                        .build();

        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(0, message.getRetryCount());
        assertTrue(message.isPending());

        message.markFailed("Kafka broker connection timeout");
        assertEquals(OutboxStatus.FAILED, message.getStatus());
        assertEquals(1, message.getRetryCount());
        assertEquals("Kafka broker connection timeout", message.getErrorMessage());
        assertNotNull(message.getProcessedAt());
        assertTrue(message.isFailed());

        message.markDeadLetter("Exceeded 3 retries");
        assertEquals(OutboxStatus.DEAD_LETTER, message.getStatus());
        assertTrue(message.isDeadLetter());
        assertEquals("Exceeded 3 retries", message.getErrorMessage());

        message.resetForRetry();
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(0, message.getRetryCount());
        assertNull(message.getErrorMessage());
        assertNull(message.getProcessedAt());

        message.markProcessed();
        assertEquals(OutboxStatus.PROCESSED, message.getStatus());
        assertNotNull(message.getProcessedAt());
        assertTrue(message.isProcessed());
    }

    @Test
    @DisplayName("OutboxService should delegate relay and DLQ methods to OutboxRelayService")
    void testRelayDelegation() {
        RelayBatchResult expectedResult =
                RelayBatchResult.builder()
                        .totalFetched(5)
                        .processed(5)
                        .failed(0)
                        .deadLettered(0)
                        .skipped(0)
                        .build();

        when(outboxRelayService.processBatch()).thenReturn(expectedResult);
        RelayBatchResult actual = outboxService.relayPendingMessages();
        assertEquals(5, actual.getProcessed());
        assertFalse(actual.hasFailures());

        UUID messageId = UUID.randomUUID();
        when(outboxRelayService.requeueDeadLetter(messageId)).thenReturn(1);
        int requeued = outboxService.requeueDeadLetter(messageId);
        assertEquals(1, requeued);

        when(outboxRelayService.requeueAllDeadLetters()).thenReturn(3);
        int requeuedAll = outboxService.requeueAllDeadLetters();
        assertEquals(3, requeuedAll);

        when(outboxMessageRepository.countByStatus(OutboxStatus.DEAD_LETTER)).thenReturn(2L);
        assertEquals(2L, outboxService.getDeadLetterCount());

        when(outboxMessageRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(10L);
        assertEquals(10L, outboxService.getPendingCount());
    }
}
