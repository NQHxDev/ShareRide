package com.shareride.common.probe.dependency;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Enterprise database health indicator monitoring connection pool metrics, query execution latency,
 * and connectivity under circuit breaker protection.
 */
public class DatabaseHealthIndicator implements HealthIndicator {

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ResilientHealthExecutor resilientExecutor;

    public DatabaseHealthIndicator(ObjectProvider<DataSource> dataSourceProvider) {
        this(dataSourceProvider, new ResilientHealthExecutor("database", 2000L));
    }

    public DatabaseHealthIndicator(
            ObjectProvider<DataSource> dataSourceProvider,
            ResilientHealthExecutor resilientExecutor) {
        this.dataSourceProvider = dataSourceProvider;
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public Health health() {
        DataSource dataSource =
                dataSourceProvider != null ? dataSourceProvider.getIfAvailable() : null;
        if (dataSource == null) {
            return Health.up()
                    .withDetail("database", "NOT_CONFIGURED")
                    .withDetail("message", "No DataSource bean configured for this microservice")
                    .build();
        }

        return resilientExecutor.execute(() -> checkDatabaseHealth(dataSource));
    }

    private Health checkDatabaseHealth(DataSource dataSource) {
        Health.Builder builder = Health.up();
        long queryStart = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            boolean valid;
            try {
                valid = conn.isValid(2);
            } catch (Throwable t) {
                // Fallback to validation query
                try (Statement stmt = conn.createStatement()) {
                    valid = stmt.execute("SELECT 1");
                }
            }

            long queryDuration = System.currentTimeMillis() - queryStart;

            if (!valid) {
                return Health.down()
                        .withDetail("database", "INVALID_CONNECTION")
                        .withDetail("queryTimeMs", queryDuration)
                        .build();
            }

            DatabaseMetaData metaData = conn.getMetaData();
            builder.withDetail("database", metaData.getDatabaseProductName())
                    .withDetail("version", metaData.getDatabaseProductVersion())
                    .withDetail("queryTimeMs", queryDuration);

            // Extract Connection Pool metrics if HikariCP or compatible pool
            inspectPoolMetrics(dataSource, builder);

            return builder.build();
        } catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }

    private void inspectPoolMetrics(DataSource dataSource, Health.Builder builder) {
        try {
            Class<?> hikariClass = Class.forName("com.zaxxer.hikari.HikariDataSource");
            if (hikariClass.isInstance(dataSource)) {
                Method getPoolBean = hikariClass.getMethod("getHikariPoolMXBean");
                Object poolBean = getPoolBean.invoke(dataSource);
                if (poolBean != null) {
                    Class<?> poolClass = poolBean.getClass();
                    int active = (int) poolClass.getMethod("getActiveConnections").invoke(poolBean);
                    int idle = (int) poolClass.getMethod("getIdleConnections").invoke(poolBean);
                    int total = (int) poolClass.getMethod("getTotalConnections").invoke(poolBean);
                    int waiting =
                            (int)
                                    poolClass
                                            .getMethod("getThreadsAwaitingConnection")
                                            .invoke(poolBean);

                    builder.withDetail("pool.activeConnections", active)
                            .withDetail("pool.idleConnections", idle)
                            .withDetail("pool.totalConnections", total)
                            .withDetail("pool.threadsAwaiting", waiting);

                    if (total > 0) {
                        double utilization = (double) active / total * 100.0;
                        builder.withDetail(
                                "pool.utilizationPercent", Math.round(utilization * 10.0) / 10.0);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Not a HikariCP pool or reflection skipped
        }
    }
}
