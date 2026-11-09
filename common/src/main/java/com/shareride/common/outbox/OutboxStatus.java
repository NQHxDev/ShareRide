package com.shareride.common.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED,
    DEAD_LETTER
}
