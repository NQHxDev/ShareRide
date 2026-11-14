package com.shareride.auth.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Valkey (Redis-compatible) configuration for auth service. Configures connection to Valkey
 * database 0 for strict service isolation.
 */
@Configuration
@EnableConfigurationProperties(BloomFilterProperties.class)
public class ValkeyConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    /**
     * Creates Lettuce connection factory configured for Valkey database 0. Database 0 is exclusive
     * to auth service for Service Per Database isolation.
     */
    @Primary
    @Bean(name = "authValkeyConnectionFactory")
    public LettuceConnectionFactory authValkeyConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);

        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        ClientResources clientResources = DefaultClientResources.create();

        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        .clientOptions(ClientOptions.builder().build())
                        .clientResources(clientResources)
                        .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    /** RedisTemplate for general Valkey operations. Uses String serializer for keys and values. */
    @Bean(name = "redisTemplate")
    public RedisTemplate<String, String> redisTemplate(
            @Qualifier("authValkeyConnectionFactory") LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
