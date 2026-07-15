package com.docai.ingestor.config;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Authenticates documentation-bot's server-to-server calls to the internal document-access API
 * (currently just {@code GET /api/internal/documents/{id}/download-url}, backing "open citation"
 * — see InternalDocumentController). The bot already resolved per-user document access via
 * DocumentAccessPolicy before making this call; the ingestor has no notion of users/grants, so
 * it trusts a valid signature rather than re-deriving ACL it doesn't have data for.
 *
 * <p>Same shape as {@link WebhookHmacAuthFilter}: {@code X-Internal-Signature: sha256=<hex
 * hmac-sha256(documentId, INTERNAL_SERVICE_SECRET)>}. A GET has no body to sign, so the path's
 * {@code id} segment is the signed payload instead.
 */
@Slf4j
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String SIGNATURE_HEADER = "X-Internal-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final Pattern DOWNLOAD_URL_PATH =
        Pattern.compile("^/api/internal/documents/([^/]+)/download-url$");

    private final String serviceSecret;

    public InternalServiceAuthFilter(@Value("${ingestor.internal.service-secret:}") String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("GET".equalsIgnoreCase(request.getMethod())
            && DOWNLOAD_URL_PATH.matcher(request.getRequestURI()).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, java.io.IOException {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            log.warn("INTERNAL_SERVICE_SECRET is not configured — the internal document-access API "
                + "will reject every request (it has no other way to authenticate).");
            filterChain.doFilter(request, response);
            return;
        }

        Matcher matcher = DOWNLOAD_URL_PATH.matcher(request.getRequestURI());
        String providedSignature = request.getHeader(SIGNATURE_HEADER);
        if (matcher.matches() && StringUtils.hasText(providedSignature)) {
            String documentId = matcher.group(1);
            if (verifySignature(documentId, providedSignature)) {
                var auth = new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean verifySignature(String documentId, String providedSignature) {
        String provided = providedSignature.startsWith(SIGNATURE_PREFIX)
            ? providedSignature.substring(SIGNATURE_PREFIX.length()) : providedSignature;
        String expected = computeHmac(documentId);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmac(String documentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serviceSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(documentId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute internal-service HMAC", e);
        }
    }
}
