package com.shareride.common.outbox;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Background scheduler periodically invoking the Outbox Relay to process and dispatch pending
 * events.
 *
 * <p>Protects against overlapping runs via an {@link AtomicBoolean} execution lock.
 */
@Slf4j
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public OutboxRelayScheduler(OutboxRelayService outboxRelayService) {
        this.outboxRelayService = outboxRelayService;
    }

    @Scheduled(
            fixedDelayString = "${shareride.outbox.relay.fixed-delay-ms:2000}",
            initialDelayString = "${shareride.outbox.relay.initial-delay-ms:5000}")
    public void scheduleRelay() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.debug("Previous outbox relay run still active; skipping scheduled trigger");
            return;
        }

        try {
            RelayBatchResult result = outboxRelayService.processBatch();
            if (result.getTotalFetched() > 0) {
                log.debug("Outbox scheduled relay execution finished: {}", result);
            }
        } catch (Exception e) {
            log.error("Unexpected error in Outbox Relay Scheduler", e);
        } finally {
            isProcessing.set(false);
        }
    }
}
