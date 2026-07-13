package com.docai.bot.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisURI;
import io.lettuce.core.RedisClient;
import org.springframework.util.StringUtils;

/**
 * Backs {@link RateLimitFilter} with a shared Redis store so request limits are enforced
 * correctly across horizontally-scaled replicas — previously each pod kept its own in-memory
 * bucket map, so the effective limit silently multiplied by replica count and reset on restart.
 *
 * <p>Only created when REDIS_HOST is actually set (Redis is optional elsewhere in this app too —
 * see RedisConfig's embedding cache). Without it, RateLimitFilter falls back to the old
 * per-instance in-memory behavior, which is still correct for a single-instance/local deploy.
 */
@Configuration
public class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public RedisClient rateLimitRedisClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        RedisURI.Builder uriBuilder = RedisURI.Builder.redis(host, port);
        if (StringUtils.hasText(password)) {
            uriBuilder.withPassword((CharSequence) password);
        }
        return RedisClient.create(uriBuilder.build());
    }

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public ProxyManager<byte[]> rateLimitProxyManager(RedisClient rateLimitRedisClient) {
        return LettuceBasedProxyManager.builderFor(rateLimitRedisClient)
            .withClientSideConfig(ClientSideConfig.getDefault()
                .withExpirationAfterWriteStrategy(
                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2))))
            .build();
    }
}
