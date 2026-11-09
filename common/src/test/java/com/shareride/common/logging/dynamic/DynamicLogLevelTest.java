package com.shareride.common.logging.dynamic;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DynamicLogLevelTest {

    private DynamicLogLevelTurboFilter filter;
    private Logger appLogger;
    private Logger externalLogger;

    @BeforeEach
    void setUp() {
        filter = new DynamicLogLevelTurboFilter();
        filter.setPackagePrefix("com.shareride");
        filter.start();

        appLogger = (Logger) LoggerFactory.getLogger("com.shareride.service.UserService");
        externalLogger = (Logger) LoggerFactory.getLogger("org.springframework.web.Http");
        DynamicLogLevelContext.clear();
    }

    @AfterEach
    void tearDown() {
        DynamicLogLevelContext.clear();
    }

    @Test
    void testDynamicDebugOverride() {
        assertFalse(DynamicLogLevelContext.isDebugActive());
        assertEquals(
                FilterReply.NEUTRAL,
                filter.decide(null, appLogger, Level.DEBUG, "debug msg", null, null));

        DynamicLogLevelContext.setDebugOverride(true);
        assertTrue(DynamicLogLevelContext.isDebugActive());
        assertEquals(
                FilterReply.ACCEPT,
                filter.decide(null, appLogger, Level.DEBUG, "debug msg", null, null));
        assertEquals(
                FilterReply.ACCEPT,
                filter.decide(null, appLogger, Level.TRACE, "trace msg", null, null));

        // External package should stay NEUTRAL
        assertEquals(
                FilterReply.NEUTRAL,
                filter.decide(null, externalLogger, Level.DEBUG, "external debug", null, null));

        // INFO level always remains NEUTRAL
        assertEquals(
                FilterReply.NEUTRAL,
                filter.decide(null, appLogger, Level.INFO, "info msg", null, null));
    }

    @Test
    void testDebugUserRegistry() {
        DynamicLogLevelContext.addDebugUser("vip-tester");
        assertTrue(DynamicLogLevelContext.isDebugUser("vip-tester"));
        assertFalse(DynamicLogLevelContext.isDebugUser("normal-user"));

        DynamicLogLevelContext.removeDebugUser("vip-tester");
        assertFalse(DynamicLogLevelContext.isDebugUser("vip-tester"));
    }

    @Test
    void testDebugEndpointRegistry() {
        DynamicLogLevelContext.addDebugEndpoint("/api/v1/rides/problematic");
        assertTrue(DynamicLogLevelContext.isDebugEndpoint("/api/v1/rides/problematic"));
        assertTrue(DynamicLogLevelContext.isDebugEndpoint("/api/v1/rides/problematic/sub"));
        assertFalse(DynamicLogLevelContext.isDebugEndpoint("/api/v1/users"));
    }
}
