package com.shareride.common.outbox;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/** Encapsulates the execution summary of a single outbox relay batch iteration. */
@Getter
@Builder
@ToString
public class RelayBatchResult {

    private final int totalFetched;
    private final int processed;
    private final int failed;
    private final int deadLettered;
    private final int skipped;

    public boolean hasFailures() {
        return failed > 0 || deadLettered > 0;
    }

    public static RelayBatchResult empty() {
        return RelayBatchResult.builder()
                .totalFetched(0)
                .processed(0)
                .failed(0)
                .deadLettered(0)
                .skipped(0)
                .build();
    }
}
