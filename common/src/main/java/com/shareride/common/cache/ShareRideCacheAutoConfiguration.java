package com.shareride.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.metrics.ShareRideMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Enterprise AutoConfiguration for Hybrid Cache architecture (backed by Valkey): 1. Shared Cache:
 * Connected to shared cluster for distributed locks, token revocation, rate limiting. 2. Local
 * Cache: Conditionally configured when shareride.cache.local.enabled=true for private microservice
 * caching.
 */
@AutoConfiguration
@ConditionalOnClass(RedisOperations.class)
@ConditionalOnProperty(
        name = "shareride.cache.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ShareRideCacheProperties.class)
public class ShareRideCacheAutoConfiguration {

    private final Environment environment;

    public ShareRideCacheAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean(name = "sharedRedisConnectionFactory")
    @ConditionalOnMissingBean(name = "sharedRedisConnectionFactory")
    public LettuceConnectionFactory sharedRedisConnectionFactory(
            ShareRideCacheProperties properties) {
        return createLettuceConnectionFactory(resolveSharedProperties(properties));
    }

    @Bean(name = "sharedRedisTemplate")
    @Primary
    @ConditionalOnMissingBean(name = "sharedRedisTemplate")
    public RedisTemplate<String, Object> sharedRedisTemplate(
            @Qualifier("sharedRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return createRedisTemplate(
                connectionFactory,
                objectMapperProvider != null ? objectMapperProvider.getIfAvailable() : null);
    }

    @Bean(name = "localRedisConnectionFactory")
    @ConditionalOnProperty(name = "shareride.cache.local.enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "localRedisConnectionFactory")
    public LettuceConnectionFactory localRedisConnectionFactory(
            ShareRideCacheProperties properties) {
        return createLettuceConnectionFactory(properties.getLocal());
    }

    @Bean(name = "localRedisTemplate")
    @ConditionalOnProperty(name = "shareride.cache.local.enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "localRedisTemplate")
    public RedisTemplate<String, Object> localRedisTemplate(
            @Qualifier("localRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return createRedisTemplate(
                connectionFactory,
                objectMapperProvider != null ? objectMapperProvider.getIfAvailable() : null);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockService distributedLockService(
            @Qualifier("sharedRedisTemplate") RedisTemplate<String, Object> sharedRedisTemplate,
            ObjectProvider<ShareRideMetrics> metricsProvider) {
        return new DistributedLockService(sharedRedisTemplate, metricsProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistService tokenBlacklistService(
            @Qualifier("sharedRedisTemplate") RedisTemplate<String, Object> sharedRedisTemplate) {
        return new TokenBlacklistService(sharedRedisTemplate);
    }

    private ShareRideCacheProperties.CacheInstanceProperties resolveSharedProperties(
            ShareRideCacheProperties properties) {
        ShareRideCacheProperties.CacheInstanceProperties shared = properties.getShared();
        // Fallback to legacy shareride.redis properties or spring.data.redis properties if
        // shareride.cache wasn't explicitly changed
        if ("localhost".equals(shared.getHost()) && environment != null) {
            String legacyHost =
                    environment.getProperty(
                            "shareride.redis.shared.host",
                            environment.getProperty("spring.data.redis.host"));
            if (legacyHost != null && !legacyHost.isBlank()) {
                shared.setHost(legacyHost);
            }
            String legacyPort =
                    environment.getProperty(
                            "shareride.redis.shared.port",
                            environment.getProperty("spring.data.redis.port"));
            if (legacyPort != null && !legacyPort.isBlank()) {
                try {
                    shared.setPort(Integer.parseInt(legacyPort));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return shared;
    }

    private LettuceConnectionFactory createLettuceConnectionFactory(
            ShareRideCacheProperties.CacheInstanceProperties props) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(props.getHost());
        config.setPort(props.getPort());
        config.setDatabase(props.getDatabase());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            config.setPassword(RedisPassword.of(props.getPassword()));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder().commandTimeout(props.getTimeout());

        if (props.isSsl()) {
            builder.useSsl();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, builder.build());
        factory.afterPropertiesSet();
        return factory;
    }

    private RedisTemplate<String, Object> createRedisTemplate(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.json());

        template.afterPropertiesSet();
        return template;
    }
}
