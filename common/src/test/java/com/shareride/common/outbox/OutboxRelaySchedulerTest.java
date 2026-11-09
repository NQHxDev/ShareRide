package com.shareride.common.outbox;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock private OutboxRelayService outboxRelayService;

    @Test
    @DisplayName("OutboxRelayScheduler scheduleRelay should invoke outboxRelayService processBatch")
    void testScheduleRelay() {
        when(outboxRelayService.processBatch()).thenReturn(RelayBatchResult.empty());

        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(outboxRelayService);
        scheduler.scheduleRelay();

        verify(outboxRelayService, times(1)).processBatch();
    }
}
