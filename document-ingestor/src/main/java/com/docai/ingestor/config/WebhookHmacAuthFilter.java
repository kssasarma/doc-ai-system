package com.docai.ingestor.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Real webhook authentication for {@code POST /api/v1/ingest/webhook}, replacing the previous
 * X-API-Key header that was only ever logged, never verified (see WebhookController javadoc
 * history). A CI/CD system triggering ingestion holds a long-lived shared secret
 * ({@code WEBHOOK_HMAC_SECRET}) rather than a short-lived JWT, and signs the exact request body
 * it sends: {@code X-Webhook-Signature: sha256=<hex hmac-sha256(body, secret)>}.
 *
 * <p>On a valid signature this sets an authenticated principal with ROLE_ADMIN so the existing
 * {@code anyRequest().hasRole("ADMIN")} rule in {@link SecurityConfig} is satisfied without a
 * JWT. A missing/invalid signature leaves the request unauthenticated — {@link JwtTokenFilter}
 * still gets a chance to authenticate it via a real JWT (e.g. an admin testing the endpoint
 * in-browser), and if neither succeeds the existing entry point returns 401.
 */
@Slf4j
@Component
public class WebhookHmacAuthFilter extends OncePerRequestFilter {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String WEBHOOK_PATH = "/api/v1/ingest/webhook";

    private final String hmacSecret;

    public WebhookHmacAuthFilter(@Value("${ingestor.webhook.hmac-secret:}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && WEBHOOK_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            log.warn("WEBHOOK_HMAC_SECRET is not configured — webhook signature verification is disabled; "
                + "the ingest webhook falls back to requiring a JWT with ADMIN role.");
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String providedSignature = request.getHeader(SIGNATURE_HEADER);

        if (StringUtils.hasText(providedSignature) && verifySignature(cachedRequest.getCachedBody(), providedSignature)) {
            var auth = new UsernamePasswordAuthenticationToken(
                "webhook", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private boolean verifySignature(byte[] body, String providedSignature) {
        String provided = providedSignature.startsWith(SIGNATURE_PREFIX)
            ? providedSignature.substring(SIGNATURE_PREFIX.length()) : providedSignature;

        String expected = computeHmac(body);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute webhook HMAC", e);
        }
    }
}
