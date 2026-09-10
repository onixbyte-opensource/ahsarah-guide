package com.onixbyte.ahsarahguide.config;

import com.onixbyte.ahsarahguide.shared.JacksonRedisSerialiser;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * Configuration class for Redis-based caching components.
 * <p>
 * This configuration class provides beans for Redis cache management and template operations
 * within the Helix application. It configures custom serialisation strategies using
 * {@link GenericJackson2JsonRedisSerializer} for values and string serialisation for keys,
 * ensuring optimal performance and compatibility with JSON-based data structures.
 * <p>
 * The configuration includes:
 * <ul>
 *   <li>Custom {@link RedisCacheManager} with JSON serialisation support</li>
 *   <li>Configured {@link RedisTemplate} for direct Redis operations</li>
 * </ul>
 *
 * @author zihluwang
 * @see RedisCacheManager
 * @see RedisTemplate
 * @see GenericJackson2JsonRedisSerializer
 * @since 1.0.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Creates the application's primary cache manager, backed by Redis.
     * <p>
     * This manager is marked {@link Primary}, so it is the one selected for any
     * {@code @Cacheable} or {@code @CacheEvict} annotation that does not name a manager
     * explicitly, as well as for any {@link CacheManager} that is injected by type.
     * <p>
     * Cache entries are keyed using string serialisation and values are stored as JSON via
     * {@link JacksonRedisSerialiser}. Every entry expires after two hours, making this manager
     * suitable for data that is expensive to compute but not required to survive for long.
     *
     * @param connectionFactory the Redis connection factory used to establish connections
     * @return a fully configured {@link CacheManager} with a two-hour default entry TTL
     * @see #longTermCacheManager(RedisConnectionFactory)
     * @see RedisCacheManager
     */
    @Primary
    @Bean
    public CacheManager defaultCacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        var _keySerializer = RedisSerializer.string();

        var cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(_keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(JacksonRedisSerialiser.INSTANCE))
                .entryTtl(Duration.ofHours(2L));

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(connectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }

    /**
     * Creates a secondary cache manager for data that must be retained for longer.
     * <p>
     * This manager is not {@link Primary}; it must be requested by name wherever it is needed,
     * as in {@code @Cacheable(cacheManager = "longTermCacheManager")}.
     * <p>
     * Serialisation is configured identically to {@link #defaultCacheManager}, but entries
     * expire after one day rather than two hours.
     *
     * @param connectionFactory the Redis connection factory used to establish connections
     * @return a fully configured {@link CacheManager} with a one-day default entry TTL
     * @see #defaultCacheManager(RedisConnectionFactory)
     * @see RedisCacheManager
     */
    @Bean
    public CacheManager longTermCacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        var _keySerializer = RedisSerializer.string();

        var cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(_keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(JacksonRedisSerialiser.INSTANCE))
                .entryTtl(Duration.ofDays(1L));

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(connectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }

    /**
     * Creates a Redis template for direct Redis operations with custom serialisation.
     * <p>
     * This method configures a {@link RedisTemplate} that uses string serialisation for keys
     * and {@link GenericJackson2JsonRedisSerializer} for values. This template provides low-level
     * access to Redis operations whilst ensuring consistent serialisation strategies across
     * the application.
     * <p>
     * The template is fully configured and ready for use after bean creation.
     *
     * @param connectionFactory the Redis connection factory used to establish connections
     * @return a fully configured {@link RedisTemplate} for Redis operations
     * @see RedisTemplate
     * @see GenericJackson2JsonRedisSerializer
     * @see RedisSerializer
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        var redisTemplate = new RedisTemplate<String, Object>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(JacksonRedisSerialiser.INSTANCE);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}

