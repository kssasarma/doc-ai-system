package com.docai.bot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.domain.entity.ApiKey;

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
 * Two independent rate limits, both bucket4j-backed (shared Redis store when configured — see
 * RateLimitConfig — so the limit holds across horizontally-scaled replicas; a local in-memory map,
 * correct only for a single instance, otherwise):
 *
 *  1. Per-user, on /api/chat/query and its streaming counterpart /api/chat/query/stream (same
 *     underlying LLM cost per call — both share one bucket per user).
 *  2. Per-IP, on the unauthenticated auth endpoints (/api/auth/login, /api/auth/refresh) — there's
 *     no user identity yet to key on, and these are exactly the endpoints a credential-stuffing /
 *     brute-force script would hammer. Deliberately tighter than the chat limit and configured
 *     separately. Complements (does not replace) UserService's per-account lockout — an attacker
 *     spreading guesses across many usernames from one IP is stopped here even though no single
 *     account ever accumulates enough failures to lock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> CHAT_PATHS = Set.of("/api/chat/query", "/api/chat/query/stream");
    private static final Set<String> AUTH_PATHS = Set.of(
        "/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password");
    private static final String API_V1_PREFIX = "/api/v1/";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_BEARER_PREFIX = "ApiKey ";

    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;

    @Autowired(required = false)
    private ProxyManager<byte[]> proxyManager;

    @Value("${app.rate-limit.requests-per-minute:30}")
    private int chatRequestsPerMinute;

    @Value("${app.rate-limit.auth-requests-per-minute:10}")
    private int authRequestsPerMinute;

    @Value("${app.rate-limit.api-key-default-per-minute:60}")
    private int defaultApiKeyRequestsPerMinute;

    /** Only used when proxyManager is absent (no Redis configured) — single-instance fallback. */
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (CHAT_PATHS.contains(uri)) {
            filterByUser(request, response, filterChain);
        } else if (AUTH_PATHS.contains(uri)) {
            filterByIp(request, response, filterChain);
        } else if (uri.startsWith(API_V1_PREFIX)) {
            filterByApiKey(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /** /api/v1/** traffic bypasses the per-user chat limit above (it's authenticated by API key,
     * not JWT) — enforce each key's own configured rateLimitPerMin instead, falling back to the
     * global default for a key that doesn't override it. An unresolvable/missing key is let
     * through here; the controller/service layer is responsible for rejecting it as unauthorized. */
    private void filterByApiKey(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        String rawKey = extractApiKey(request);
        if (rawKey == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<ApiKey> apiKey = apiKeyService.validateKey(rawKey);
        if (apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        int limit = apiKey.get().getRateLimitPerMin() > 0
            ? apiKey.get().getRateLimitPerMin() : defaultApiKeyRequestsPerMinute;
        enforce("rate-limit:apikey:" + apiKey.get().getId(), () -> buildBandwidth(limit),
            limit, request, response, filterChain);
    }

    private static String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) return header.trim();
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(API_KEY_BEARER_PREFIX)) {
            return auth.substring(API_KEY_BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void filterByUser(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            UUID userId = jwtService.extractUserId(authHeader.substring(7));
            enforce("rate-limit:user:" + userId, () -> buildBandwidth(chatRequestsPerMinute),
                chatRequestsPerMinute, request, response, filterChain);
        } catch (Exception e) {
            // Malformed/expired token — let the auth filter handle it
            filterChain.doFilter(request, response);
        }
    }

    private void filterByIp(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        String ip = clientIp(request);
        enforce("rate-limit:ip:" + ip, () -> buildBandwidth(authRequestsPerMinute),
            authRequestsPerMinute, request, response, filterChain);
    }

    private void enforce(String key, Supplier<Bandwidth> bandwidth, int limit,
                         HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        Bucket bucket = resolveBucket(key, bandwidth);
        if (bucket.tryConsume(1)) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.addHeader("X-RateLimit-Remaining", "0");
            response.getWriter().write(
                """
                {"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests. Limit is %d per minute."}
                """.formatted(limit).strip()
            );
        }
    }

    private Bucket resolveBucket(String key, Supplier<Bandwidth> bandwidth) {
        if (proxyManager != null) {
            byte[] proxyKey = key.getBytes(StandardCharsets.UTF_8);
            return proxyManager.builder().build(proxyKey,
                () -> BucketConfiguration.builder().addLimit(bandwidth.get()).build());
        }
        return localBuckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(bandwidth.get()).build());
    }

    private static Bandwidth buildBandwidth(int perMinute) {
        return Bandwidth.builder()
            .capacity(perMinute)
            .refillGreedy(perMinute, Duration.ofMinutes(1))
            .build();
    }

    /** Behind the nginx ingress this app is deployed behind (see README architecture), the first
     * X-Forwarded-For entry is the original client IP; falls back to the socket address for direct
     * (e.g. local dev) connections. */
    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
