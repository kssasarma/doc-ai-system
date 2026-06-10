package com.docai.bot.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.docai.bot.application.service.JwtService;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Value("${app.rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

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
            Bucket bucket = buckets.computeIfAbsent(userId, id -> buildBucket());

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

    private Bucket buildBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build())
            .build();
    }
}
