package com.shareride.common.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.metrics.ShareRideMetrics;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock private OutboxMessageRepository outboxMessageRepository;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private OutboxDeadLetterHandler deadLetterHandler;
    @Mock private ObjectProvider<ShareRideMetrics> metricsProvider;
    @Mock private ShareRideMetrics metrics;

    private OutboxProperties outboxProperties;
    private OutboxRelayService outboxRelayService;

    @BeforeEach
    void setUp() {
        outboxProperties = new OutboxProperties();
        outboxProperties.setBatchSize(10);
        outboxProperties.setMaxRetries(3);
        outboxProperties.setRetryBackoffMs(1000L);
        outboxProperties.setBackoffMultiplier(2.0);
        outboxProperties.setMaxBackoffMs(10000L);

        lenient().when(metricsProvider.getIfAvailable()).thenReturn(metrics);

        outboxRelayService =
                new OutboxRelayService(
                        outboxMessageRepository,
                        outboxPublisher,
                        deadLetterHandler,
                        outboxProperties,
                        metricsProvider);
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Process empty batch should return empty RelayBatchResult")
    void testProcessBatchEmpty() {
        when(outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        RelayBatchResult result = outboxRelayService.processBatch();

        assertEquals(0, result.getTotalFetched());
        assertEquals(0, result.getProcessed());
        assertEquals(0, result.getFailed());
        assertEquals(0, result.getDeadLettered());
        assertEquals(0, result.getSkipped());
        assertFalse(result.hasFailures());
    }

    @Test
    @DisplayName("Batch relay should publish pending messages and transition status to PROCESSED")
    void testProcessBatchSuccess() throws Exception {
        OutboxMessage msg1 =
                OutboxMessage.builder()
                        .aggregateType("User")
                        .aggregateId("u-1")
                        .eventType("USER_REGISTERED")
                        .payload("{\"id\":\"u-1\"}")
                        .traceId("trace-111")
                        .status(OutboxStatus.PENDING)
                        .build();

        OutboxMessage msg2 =
                OutboxMessage.builder()
                        .aggregateType("User")
                        .aggregateId("u-2")
                        .eventType("USER_REGISTERED")
                        .payload("{\"id\":\"u-2\"}")
                        .traceId("trace-222")
                        .status(OutboxStatus.PENDING)
                        .build();

        when(outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(msg1, msg2));
        when(outboxMessageRepository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RelayBatchResult result = outboxRelayService.processBatch();

        assertEquals(2, result.getTotalFetched());
        assertEquals(2, result.getProcessed());
        assertEquals(0, result.getFailed());
        assertEquals(0, result.getDeadLettered());
        assertEquals(0, result.getSkipped());

        verify(outboxPublisher, times(1)).publish(msg1);
        verify(outboxPublisher, times(1)).publish(msg2);

        assertEquals(OutboxStatus.PROCESSED, msg1.getStatus());
        assertNotNull(msg1.getProcessedAt());
        assertEquals(OutboxStatus.PROCESSED, msg2.getStatus());
        assertNotNull(msg2.getProcessedAt());

        verify(metrics, times(2))
                .incrementCounter(
                        eq("outbox.relayed.total"),
                        eq("aggregateType"),
                        eq("User"),
                        eq("eventType"),
                        eq("USER_REGISTERED"));
    }

    @Test
    @DisplayName("Publisher failure should increment retry count and transition status to FAILED")
    void testProcessBatchRetryOnTransientFailure() throws Exception {
        OutboxMessage msg =
                OutboxMessage.builder()
                        .aggregateType("Ride")
                        .aggregateId("ride-999")
                        .eventType("RIDE_REQUESTED")
                        .payload("{}")
                        .traceId("trace-err")
                        .status(OutboxStatus.PENDING)
                        .retryCount(0)
                        .build();

        when(outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(msg));
        when(outboxMessageRepository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Kafka broker connection timeout"))
                .when(outboxPublisher)
                .publish(msg);

        RelayBatchResult result = outboxRelayService.processBatch();

        assertEquals(1, result.getTotalFetched());
        assertEquals(0, result.getProcessed());
        assertEquals(1, result.getFailed());
        assertEquals(0, result.getDeadLettered());
        assertTrue(result.hasFailures());

        assertEquals(OutboxStatus.FAILED, msg.getStatus());
        assertEquals(1, msg.getRetryCount());
        assertNotNull(msg.getProcessedAt());
        assertTrue(msg.getErrorMessage().contains("Kafka broker connection timeout"));

        verify(deadLetterHandler, never()).handle(any());
        verify(metrics, times(1))
                .incrementCounter(
                        eq("outbox.retries.total"),
                        eq("aggregateType"),
                        eq("Ride"),
                        eq("eventType"),
                        eq("RIDE_REQUESTED"));
    }

    @Test
    @DisplayName("Exponential backoff should skip failed messages until required delay has elapsed")
    void testProcessBatchExponentialBackoff() throws Exception {
        // Message failed 100ms ago, retryCount=1 => backoff = 1000ms => should be skipped
        OutboxMessage unreadyMessage =
                OutboxMessage.builder()
                        .aggregateType("User")
                        .aggregateId("u-unready")
                        .eventType("USER_UPDATED")
                        .payload("{}")
                        .status(OutboxStatus.FAILED)
                        .retryCount(1)
                        .processedAt(Instant.now().minus(100, ChronoUnit.MILLIS))
                        .build();

        // Message failed 5000ms ago, retryCount=1 => backoff = 1000ms => eligible for retry
        OutboxMessage readyMessage =
                OutboxMessage.builder()
                        .aggregateType("User")
                        .aggregateId("u-ready")
                        .eventType("USER_UPDATED")
                        .payload("{}")
                        .status(OutboxStatus.FAILED)
                        .retryCount(1)
                        .processedAt(Instant.now().minus(5000, ChronoUnit.MILLIS))
                        .build();

        when(outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(unreadyMessage, readyMessage));
        when(outboxMessageRepository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RelayBatchResult result = outboxRelayService.processBatch();

        assertEquals(2, result.getTotalFetched());
        assertEquals(1, result.getProcessed());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getFailed());

        verify(outboxPublisher, never()).publish(unreadyMessage);
        verify(outboxPublisher, times(1)).publish(readyMessage);
        assertEquals(OutboxStatus.PROCESSED, readyMessage.getStatus());
    }

    @Test
    @DisplayName(
            "Exceeding maximum retries should route message to Dead Letter Queue and notify DLQ"
                    + " handler")
    void testProcessBatchRoutesToDeadLetterQueue() throws Exception {
        // Message with retryCount=2 (maxRetries=3), next failure will reach 3
        OutboxMessage message =
                OutboxMessage.builder()
                        .aggregateType("Order")
                        .aggregateId("ord-777")
                        .eventType("ORDER_CREATED")
                        .payload("{}")
                        .status(OutboxStatus.FAILED)
                        .retryCount(2)
                        .processedAt(Instant.now().minus(10, ChronoUnit.SECONDS))
                        .build();

        when(outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(outboxMessageRepository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Permanent schema serialization failure"))
                .when(outboxPublisher)
                .publish(message);

        RelayBatchResult result = outboxRelayService.processBatch();

        assertEquals(1, result.getTotalFetched());
        assertEquals(0, result.getProcessed());
        assertEquals(0, result.getFailed());
        assertEquals(1, result.getDeadLettered());
        assertTrue(result.hasFailures());

        assertEquals(OutboxStatus.DEAD_LETTER, message.getStatus());
        assertTrue(message.isDeadLetter());
        assertTrue(message.getErrorMessage().contains("Exceeded maximum retries [3]"));

        ArgumentCaptor<OutboxMessage> dlqCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(deadLetterHandler, times(1)).handle(dlqCaptor.capture());
        assertEquals("ord-777", dlqCaptor.getValue().getAggregateId());
    }

    @Test
    @DisplayName(
            "TraceId and MDC context should be restored during publication and cleared afterwards")
    void testTraceIdPropagationDuringRelay() throws Exception {
        OutboxMessage message =
                OutboxMessage.builder()
                        .aggregateType("Tenant")
                        .aggregateId("t-01")
                        .eventType("TENANT_CREATED")
                        .payload("{}")
                        .traceId("trace-ctx-999")
                        .status(OutboxStatus.PENDING)
                        .build();

        when(outboxMessageRepository.findByStatusInOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(message));

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedMdcTraceId = new AtomicReference<>();

        doAnswer(
                        inv -> {
                            capturedTraceId.set(TraceIdContext.getTraceId());
                            capturedMdcTraceId.set(MDC.get(MdcConstants.TRACE_ID));
                            return null;
                        })
                .when(outboxPublisher)
                .publish(message);

        outboxRelayService.processBatch();

        assertEquals("trace-ctx-999", capturedTraceId.get());
        assertEquals("trace-ctx-999", capturedMdcTraceId.get());

        // Context should be cleared after publication
        assertNull(TraceIdContext.getRawTraceId());
        assertFalse(TraceIdContext.hasTraceId());
        assertNull(MDC.get(MdcConstants.TRACE_ID));
    }

    @Test
    @DisplayName("Requeue dead letters should update database status back to PENDING")
    void testRequeueDeadLetters() {
        UUID messageId = UUID.randomUUID();
        when(outboxMessageRepository.resetStatusById(messageId, OutboxStatus.PENDING))
                .thenReturn(1);

        int updated = outboxRelayService.requeueDeadLetter(messageId);
        assertEquals(1, updated);
        verify(outboxMessageRepository, times(1)).resetStatusById(messageId, OutboxStatus.PENDING);

        when(outboxMessageRepository.resetStatusByTargetStatus(
                        OutboxStatus.DEAD_LETTER, OutboxStatus.PENDING))
                .thenReturn(5);

        int bulkUpdated = outboxRelayService.requeueAllDeadLetters();
        assertEquals(5, bulkUpdated);
        verify(outboxMessageRepository, times(1))
                .resetStatusByTargetStatus(OutboxStatus.DEAD_LETTER, OutboxStatus.PENDING);
    }
}
