package com.shareride.common.logging.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized domain business event record for analytics and operational auditing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessEvent implements Serializable {

    private String eventId;
    private String type;
    private String userId;
    private String entityId;
    private Instant timestamp;
    private String traceId;
    private Map<String, Object> metadata;

    public String getRideId() {
        return entityId;
    }

    public void setRideId(String rideId) {
        this.entityId = rideId;
    }

    public static BusinessEventBuilder builder() {
        return new BusinessEventBuilder();
    }

    public static class BusinessEventBuilder {
        protected String eventId = UUID.randomUUID().toString();
        protected String type;
        protected String userId;
        protected String entityId;
        protected Instant timestamp = Instant.now();
        protected String traceId;
        protected Map<String, Object> metadata;

        public BusinessEventBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public BusinessEventBuilder type(String type) {
            this.type = type;
            return this;
        }

        public BusinessEventBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public BusinessEventBuilder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public BusinessEventBuilder rideId(String rideId) {
            this.entityId = rideId;
            return this;
        }

        public BusinessEventBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public BusinessEventBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public BusinessEventBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public BusinessEvent build() {
            return new BusinessEvent(eventId, type, userId, entityId, timestamp, traceId, metadata);
        }
    }
}
