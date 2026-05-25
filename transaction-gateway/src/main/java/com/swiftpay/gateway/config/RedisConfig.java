package com.swiftpay.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis wiring for the gateway. Currently exposes a single
 * {@link StringRedisTemplate} used by {@link com.swiftpay.gateway.cache.IdempotencyService}.
 *
 * <p>Connection details (host, port, ACL user/password) come from
 * {@code spring.data.redis.*} in application.yml.</p>
 */
@Configuration
public class RedisConfig {

    /** String-typed template — both idempotency keys and values are plain strings. */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}