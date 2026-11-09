package com.shareride.common.redis;

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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Enterprise AutoConfiguration for Hybrid Redis architecture: 1. Shared Redis: Connected to shared
 * cluster for distributed locks, token revocation, rate limiting. 2. Local Redis: Conditionally
 * configured when shareride.redis.local.enabled=true for private caching.
 */
@AutoConfiguration
@ConditionalOnClass(RedisOperations.class)
@ConditionalOnProperty(
        name = "shareride.redis.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ShareRideRedisProperties.class)
public class ShareRideRedisAutoConfiguration {

    @Bean(name = "sharedRedisConnectionFactory")
    @ConditionalOnMissingBean(name = "sharedRedisConnectionFactory")
    public LettuceConnectionFactory sharedRedisConnectionFactory(
            ShareRideRedisProperties properties) {
        return createLettuceConnectionFactory(properties.getShared());
    }

    @Bean(name = "sharedRedisTemplate")
    @Primary
    @ConditionalOnMissingBean(name = "sharedRedisTemplate")
    public RedisTemplate<String, Object> sharedRedisTemplate(
            @Qualifier("sharedRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        return createRedisTemplate(connectionFactory, objectMapper);
    }

    @Bean(name = "localRedisConnectionFactory")
    @ConditionalOnProperty(name = "shareride.redis.local.enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "localRedisConnectionFactory")
    public LettuceConnectionFactory localRedisConnectionFactory(
            ShareRideRedisProperties properties) {
        return createLettuceConnectionFactory(properties.getLocal());
    }

    @Bean(name = "localRedisTemplate")
    @ConditionalOnProperty(name = "shareride.redis.local.enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "localRedisTemplate")
    public RedisTemplate<String, Object> localRedisTemplate(
            @Qualifier("localRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        return createRedisTemplate(connectionFactory, objectMapper);
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

    private LettuceConnectionFactory createLettuceConnectionFactory(
            ShareRideRedisProperties.RedisInstanceProperties props) {
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
