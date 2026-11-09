package com.shareride.common.outbox;

import com.shareride.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Standard entity for the Transactional Outbox Pattern in microservices.
 *
 * <p>Ensures reliable asynchronous event publishing without dual-write hazards or 2PC overhead.
 */
@Entity
@Table(
        name = "outbox_messages",
        indexes = {
            @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
            @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxMessage extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    @Override
    protected void ensureId() {
        super.ensureId();
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage =
                error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
        this.retryCount++;
    }
}
