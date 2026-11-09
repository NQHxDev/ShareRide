package com.shareride.common.probe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shareride.common.probe.dependency.*;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.support.GenericApplicationContext;

class DependencyHealthIndicatorTest {

    @Test
    @DisplayName(
            "DatabaseHealthIndicator should report UP (NOT_CONFIGURED) when no DataSource bean"
                    + " exists")
    void testDatabaseHealthWithoutDataSource() {
        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(null);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("NOT_CONFIGURED", health.getDetails().get("database"));
    }

    @Test
    @DisplayName(
            "DatabaseHealthIndicator should report UP and inspect metadata when DataSource is"
                    + " valid")
    void testDatabaseHealthWithValidDataSource() throws SQLException {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.isValid(2)).thenReturn(true);
        when(mockConn.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(mockMeta.getDatabaseProductVersion()).thenReturn("16.2");

        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            ctx.registerBean("dataSource", DataSource.class, () -> mockDs);
            ctx.refresh();

            DatabaseHealthIndicator indicator =
                    new DatabaseHealthIndicator(ctx.getBeanProvider(DataSource.class));
            Health health = indicator.health();

            assertEquals(Status.UP, health.getStatus());
            assertEquals("PostgreSQL", health.getDetails().get("database"));
            assertEquals("16.2", health.getDetails().get("version"));
            assertNotNull(health.getDetails().get("queryTimeMs"));
        }
    }

    @Test
    @DisplayName(
            "ExternalApiHealthIndicator should report UP when empty, DEGRADED on non-critical, and"
                    + " DOWN on critical failure")
    void testExternalApiHealthIndicator() {
        ExternalDependencyRegistry registry = new ExternalDependencyRegistry();
        ExternalApiHealthIndicator indicator = new ExternalApiHealthIndicator(registry);

        // 1. Empty registry -> UP
        assertEquals(Status.UP, indicator.health().getStatus());

        // 2. Add non-critical dependency that fails -> DEGRADED
        registry.register(
                "maps-api",
                () -> Health.down().withDetail("reason", "quota_exceeded").build(),
                false);
        Health degradedHealth = indicator.health();
        assertEquals("DEGRADED", degradedHealth.getStatus().getCode());

        // 3. Add critical dependency that fails -> DOWN
        registry.register(
                "stripe-payment",
                () -> Health.down().withDetail("reason", "503_service_unavailable").build(),
                true);
        Health downHealth = indicator.health();
        assertEquals(Status.DOWN, downHealth.getStatus());
    }

    @Test
    @DisplayName("ShareRideDiskSpaceHealthIndicator should check disk threshold")
    void testDiskSpaceHealthIndicator() {
        File currentDir = new File(".");
        ShareRideDiskSpaceHealthIndicator indicator =
                new ShareRideDiskSpaceHealthIndicator(currentDir, 1024L);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertTrue(((Number) health.getDetails().get("freeMb")).longValue() > 0);

        // Unattainable threshold (e.g. 100 Petabytes) -> DOWN
        ShareRideDiskSpaceHealthIndicator hugeThreshold =
                new ShareRideDiskSpaceHealthIndicator(currentDir, Long.MAX_VALUE);
        assertEquals(Status.DOWN, hugeThreshold.health().getStatus());
    }

    @Test
    @DisplayName(
            "StartupProbeHealthIndicator transitions from INITIALIZING to READY upon markComplete")
    void testStartupProbeTransitions() {
        StartupProbeHealthIndicator startup = new StartupProbeHealthIndicator(60L);
        assertFalse(startup.isComplete());
        assertEquals(Status.DOWN, startup.health().getStatus());
        assertEquals("INITIALIZING", startup.health().getDetails().get("state"));

        startup.markComplete();
        assertTrue(startup.isComplete());
        assertEquals(Status.UP, startup.health().getStatus());
        assertEquals("READY", startup.health().getDetails().get("state"));
        assertNotNull(startup.health().getDetails().get("startupDurationMs"));
    }

    @Test
    @DisplayName("StartupProbeHealthIndicator marks FAILED when timeout exceeded")
    void testStartupProbeTimeout() throws InterruptedException {
        // 0 second timeout -> instantly fails on health() call
        StartupProbeHealthIndicator startup = new StartupProbeHealthIndicator(0L);
        Thread.sleep(10L);

        Health health = startup.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("FAILED", health.getDetails().get("state"));
        assertTrue(
                health.getDetails()
                        .get("failureReason")
                        .toString()
                        .contains("Startup timeout exceeded"));
    }
}
