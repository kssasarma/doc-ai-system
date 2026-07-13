package com.docai.bot.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps every request with a correlation id (accepting one the caller already set via
 * {@code X-Request-Id} — e.g. an upstream gateway or the other backend service — so a single
 * user-facing action that spans both {@code documentation-bot} and {@code document-ingestor} can
 * be traced across both services' logs by the same id) and puts it in MDC as {@code traceId},
 * which {@code logback-spring.xml}'s JSON encoder already includes per log line in the {@code
 * prod} profile. Echoed back in the response so a caller that generated it can correlate their
 * own logs too, and picked up by {@link GlobalExceptionHandler} so an error response's traceId
 * always matches the request's own log lines instead of a disconnected fresh UUID.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
