package com.docai.bot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.docai.bot.application.service.JwtService;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-user rate limiting on /api/chat/query. Backed by a shared Redis store (see
 * RateLimitConfig) when one is configured, so the limit holds across horizontally-scaled
 * replicas; falls back to a local in-memory bucket map — correct only for a single instance —
 * when Redis isn't configured at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Autowired(required = false)
    private ProxyManager<byte[]> proxyManager;

    @Value("${app.rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;

    /** Only used when proxyManager is absent (no Redis configured) — single-instance fallback. */
    private final Map<UUID, Bucket> localBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!"/api/chat/query".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = jwtService.extractUserId(authHeader.substring(7));
            Bucket bucket = resolveBucket(userId);

            if (bucket.tryConsume(1)) {
                response.addHeader("X-RateLimit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for user {}", userId);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.addHeader("X-RateLimit-Remaining", "0");
                response.getWriter().write(
                    """
                    {"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests. Limit is %d per minute."}
                    """.formatted(requestsPerMinute).strip()
                );
            }
        } catch (Exception e) {
            // Malformed/expired token — let the auth filter handle it
            filterChain.doFilter(request, response);
        }
    }

    private Bucket resolveBucket(UUID userId) {
        if (proxyManager != null) {
            byte[] key = ("rate-limit:" + userId).getBytes(StandardCharsets.UTF_8);
            return proxyManager.builder().build(key, this::bucketConfiguration);
        }
        return localBuckets.computeIfAbsent(userId, id -> Bucket.builder()
            .addLimit(buildBandwidth())
            .build());
    }

    private BucketConfiguration bucketConfiguration() {
        return BucketConfiguration.builder()
            .addLimit(buildBandwidth())
            .build();
    }

    private Bandwidth buildBandwidth() {
        return Bandwidth.builder()
            .capacity(requestsPerMinute)
            .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
            .build();
    }
}
