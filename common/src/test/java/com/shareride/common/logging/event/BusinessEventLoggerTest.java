package com.shareride.common.logging.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shareride.common.filter.TraceIdContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessEventLoggerTest {

    private BusinessEventLogger logger;

    @BeforeEach
    void setUp() {
        logger = new BusinessEventLogger();
        TraceIdContext.setTraceId("trace-event-001");
    }

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
    }

    @Test
    void testPublishBusinessEvent() {
        BusinessEvent event =
                BusinessEvent.builder()
                        .type("RIDE_BOOKED")
                        .userId("u-555")
                        .rideId("ride-777")
                        .metadata(
                                Map.of(
                                        "origin",
                                        "District 1",
                                        "destination",
                                        "Airport",
                                        "paymentToken",
                                        "secret-token"))
                        .build();

        assertEquals("ride-777", event.getRideId());
        assertDoesNotThrow(() -> logger.publish(event));
        assertDoesNotThrow(() -> logger.publish(null));
    }

    @Test
    void testPublishStandardizedEvent() {
        Event event =
                Event.builder()
                        .type("RIDE_BOOKED")
                        .userId("u-123")
                        .rideId("ride-456")
                        .metadata(Map.of("origin", "District 1", "destination", "Airport"))
                        .build();

        assertEquals("ride-456", event.getRideId());
        assertEquals("ride-456", event.getEntityId());
        assertDoesNotThrow(() -> logger.publish(event));
    }
}
