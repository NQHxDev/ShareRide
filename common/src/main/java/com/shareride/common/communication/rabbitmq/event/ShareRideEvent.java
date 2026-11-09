package com.shareride.common.communication.rabbitmq.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Enterprise standard Asynchronous Event Envelope for microservices communication over RabbitMQ.
 * Carries distributed trace headers, audit timestamps, originator service, and strongly-typed
 * payload.
 *
 * @param <T> Payload type
 */
@Getter
@Builder
@ToString
public class ShareRideEvent<T> implements Serializable {

    private final String eventId;
    private final String eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final Instant timestamp;
    private final String producer;

    // Tracing & Security Context
    private final String traceId;
    private final String spanId;
    private final String correlationId;
    private final String userId;
    private final String tenantId;

    // Actual Event Payload
    private final T payload;

    @JsonCreator
    public ShareRideEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("producer") String producer,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("spanId") String spanId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("userId") String userId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("payload") T payload) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.producer = producer;
        this.traceId = traceId;
        this.spanId = spanId;
        this.correlationId = correlationId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.payload = payload;
    }

    /**
     * Factory method creating a new event, automatically capturing the current distributed trace
     * context and user identity from thread-local variables.
     */
    public static <T> ShareRideEvent<T> create(
            String eventType,
            String aggregateType,
            String aggregateId,
            String producer,
            T payload) {
        String currentTraceId = TraceIdContext.getTraceId();
        String currentSpanId = TraceIdContext.getSpanId();
        UserContext userContext = UserContextHolder.getContext();

        String userId = (userContext != null) ? userContext.getUserId() : null;
        String tenantId = (userContext != null) ? userContext.getTenantId() : null;
        String correlationId =
                (userContext != null && userContext.getCorrelationId() != null)
                        ? userContext.getCorrelationId()
                        : currentTraceId;

        return ShareRideEvent.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .timestamp(Instant.now())
                .producer(producer)
                .traceId(currentTraceId)
                .spanId(currentSpanId)
                .correlationId(correlationId)
                .userId(userId)
                .tenantId(tenantId)
                .payload(payload)
                .build();
    }
}
