package com.docai.bot.application.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed cache for query embeddings.
 * Cache key: "emb::<sha256 of text>" — TTL 1 hour.
 * Active only when spring.data.redis.host is configured.
 * Falls back gracefully when Redis is unavailable.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
@RequiredArgsConstructor
public class EmbeddingCacheService {

    private static final String KEY_PREFIX = "emb::";
    private static final long TTL_SECONDS  = 3600;

    private final RedisTemplate<String, List<Double>> redisTemplate;

    public Optional<List<Double>> get(String text) {
        try {
            String key  = key(text);
            List<Double> cached = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(cached);
        } catch (Exception e) {
            log.warn("Redis embedding cache GET failed (ignored): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String text, List<Double> embedding) {
        try {
            redisTemplate.opsForValue().set(key(text), embedding, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis embedding cache PUT failed (ignored): {}", e.getMessage());
        }
    }

    private String key(String text) {
        // SHA-256 hex to keep keys short and safe
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(KEY_PREFIX);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return KEY_PREFIX + text.hashCode();
        }
    }
}
