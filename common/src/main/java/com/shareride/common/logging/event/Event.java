package com.shareride.common.logging.event;

import java.time.Instant;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Fluent builder and convenience subclass of {@link BusinessEvent}.
 *
 * <p>Usage:
 *
 * <pre>
 *   eventLogger.logEvent(Event.builder()
 *       .type("RIDE_BOOKED")
 *       .userId(userId)
 *       .rideId(rideId)
 *       .correlationId(correlationId)
 *       .metadata(Map.of("origin", origin, "destination", dest))
 *       .build());
 * </pre>
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Event extends BusinessEvent {

    public Event(
            String eventId,
            String type,
            String userId,
            String entityId,
            Instant timestamp,
            String traceId,
            Map<String, Object> metadata) {
        super(eventId, type, userId, entityId, timestamp, traceId, metadata);
    }

    public Event(
            String eventId,
            String type,
            String userId,
            String entityId,
            Instant timestamp,
            String traceId,
            String correlationId,
            Map<String, Object> metadata) {
        super(eventId, type, userId, entityId, timestamp, traceId, correlationId, metadata);
    }

    public static EventBuilder builder() {
        return new EventBuilder();
    }

    public static class EventBuilder extends BusinessEvent.BusinessEventBuilder {

        @Override
        public EventBuilder eventId(String eventId) {
            super.eventId(eventId);
            return this;
        }

        @Override
        public EventBuilder type(String type) {
            super.type(type);
            return this;
        }

        @Override
        public EventBuilder userId(String userId) {
            super.userId(userId);
            return this;
        }

        @Override
        public EventBuilder entityId(String entityId) {
            super.entityId(entityId);
            return this;
        }

        @Override
        public EventBuilder rideId(String rideId) {
            super.rideId(rideId);
            return this;
        }

        @Override
        public EventBuilder timestamp(Instant timestamp) {
            super.timestamp(timestamp);
            return this;
        }

        @Override
        public EventBuilder traceId(String traceId) {
            super.traceId(traceId);
            return this;
        }

        @Override
        public EventBuilder correlationId(String correlationId) {
            super.correlationId(correlationId);
            return this;
        }

        @Override
        public EventBuilder metadata(Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        @Override
        public Event build() {
            return new Event(
                    eventId, type, userId, entityId, timestamp, traceId, correlationId, metadata);
        }
    }
}
