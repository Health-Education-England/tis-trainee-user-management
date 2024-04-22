/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.usermanagement.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * Configuration for caching behaviour.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

  private final String prefix;
  private final Duration ttl;

  /**
   * Configuration for caching behaviour.
   *
   * @param prefix The cache key prefix.
   * @param ttl    The time-to-live for cached data.
   */
  CacheConfiguration(@Value("${application.cache.key-prefix}") String prefix,
      @Value("${application.cache.time-to-live}") Duration ttl) {
    this.prefix = prefix;
    this.ttl = ttl;
  }

  /**
   * Configuration for the general data accessor.
   *
   * @return a RedisTemplate
   */
  @Bean
  public RedisTemplate<String, String> redisTemplate(
      LettuceConnectionFactory lettuceConnectionFactory) {
    RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
    redisTemplate.setKeySerializer(new GenericJackson2JsonRedisSerializer());
    redisTemplate.setConnectionFactory(lettuceConnectionFactory);
    return redisTemplate;
  }

  /**
   * Create a cache manager with configured TTL and Prefix.
   *
   * @param factory The connection factory to use.
   * @return The built cache manager.
   */
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(ttl)
        .prefixCacheNameWith(prefix + CacheKeyPrefix.SEPARATOR);

    return RedisCacheManagerBuilder.fromConnectionFactory(factory)
        .cacheDefaults(configuration)
        .build();
  }
}
