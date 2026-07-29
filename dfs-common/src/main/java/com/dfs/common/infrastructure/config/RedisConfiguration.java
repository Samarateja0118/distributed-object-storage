package com.dfs.common.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;

/**
 * Shared Redis template bean for caching, rate limiting, and distributed locks.
 */
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfiguration {

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
