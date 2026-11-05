package com.shareride.common.probe.dependency;

import org.springframework.context.ApplicationContext;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.lang.reflect.Method;

/**
 * Enterprise Redis and cache health indicator with timeout isolation and circuit breaking.
 */
public class RedisHealthIndicator implements HealthIndicator {

    private final ApplicationContext applicationContext;
    private final ResilientHealthExecutor resilientExecutor;

    public RedisHealthIndicator(ApplicationContext applicationContext) {
        this(applicationContext, new ResilientHealthExecutor("redis", 1500L));
    }

    public RedisHealthIndicator(ApplicationContext applicationContext, ResilientHealthExecutor resilientExecutor) {
        this.applicationContext = applicationContext;
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public Health health() {
        Object redisConnectionFactory = findRedisConnectionFactory();
        if (redisConnectionFactory == null) {
            return Health.up()
                    .withDetail("redis", "NOT_CONFIGURED")
                    .withDetail("message", "No RedisConnectionFactory bean configured for this microservice")
                    .build();
        }

        return resilientExecutor.execute(() -> checkRedisHealth(redisConnectionFactory));
    }

    private Health checkRedisHealth(Object connectionFactory) {
        try {
            Method getConnectionMethod = connectionFactory.getClass().getMethod("getConnection");
            Object connection = getConnectionMethod.invoke(connectionFactory);
            if (connection == null) {
                return Health.down().withDetail("redis", "CANNOT_OBTAIN_CONNECTION").build();
            }

            try {
                long pingStart = System.currentTimeMillis();
                Method pingMethod = connection.getClass().getMethod("ping");
                Object pingResult = pingMethod.invoke(connection);
                long latency = System.currentTimeMillis() - pingStart;

                boolean ok = "PONG".equalsIgnoreCase(String.valueOf(pingResult));
                if (ok) {
                    return Health.up()
                            .withDetail("redis", "PONG")
                            .withDetail("pingTimeMs", latency)
                            .build();
                } else {
                    return Health.down()
                            .withDetail("redis", "UNEXPECTED_PING_RESPONSE: " + pingResult)
                            .build();
                }
            } finally {
                try {
                    Method closeMethod = connection.getClass().getMethod("close");
                    closeMethod.invoke(connection);
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private Object findRedisConnectionFactory() {
        if (applicationContext == null) {
            return null;
        }
        try {
            Class<?> factoryClass = Class.forName("org.springframework.data.redis.connection.RedisConnectionFactory");
            String[] names = applicationContext.getBeanNamesForType(factoryClass);
            if (names.length > 0) {
                return applicationContext.getBean(names[0]);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
