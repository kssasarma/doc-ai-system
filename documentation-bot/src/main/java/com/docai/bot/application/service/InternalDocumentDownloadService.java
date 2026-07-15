package com.docai.bot.application.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * "Open citation" backend: resolves a presigned download URL for a document's original file by
 * calling document-ingestor's internal API, HMAC-signed the same way as
 * {@code InternalServiceAuthFilter} on that side expects. Callers (DocumentDownloadController)
 * must already have verified the requesting user's document access via
 * {@link DocumentAccessPolicy} — this service does not re-check that, it only knows how to reach
 * document-ingestor's storage layer.
 */
@Slf4j
@Service
public class InternalDocumentDownloadService {

    private final RestTemplate restTemplate;
    private final String ingestorUrl;
    private final String serviceSecret;

    public InternalDocumentDownloadService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${bot.internal.ingestor-url}") String ingestorUrl,
            @Value("${bot.internal.service-secret:}") String serviceSecret) {
        this.restTemplate = restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .build();
        this.ingestorUrl = ingestorUrl;
        this.serviceSecret = serviceSecret;
    }

    public record DownloadUrlResult(String url, int expiresInSeconds) {}

    /** @throws IllegalStateException if the secret isn't configured, the file is gone, or the call fails.
     *  @throws java.util.NoSuchElementException if document-ingestor doesn't know this document id. */
    public DownloadUrlResult resolveDownloadUrl(UUID documentId) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException(
                "INTERNAL_SERVICE_SECRET is not configured — citations cannot be opened.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Signature", "sha256=" + computeHmac(documentId.toString()));
        String url = ingestorUrl + "/api/internal/documents/" + documentId + "/download-url";

        try {
            ResponseEntity<IngestorDownloadUrlResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), IngestorDownloadUrlResponse.class);
            IngestorDownloadUrlResponse body = response.getBody();
            if (body == null || body.getUrl() == null) {
                throw new IllegalStateException(body != null && body.getError() != null
                    ? body.getError() : "document-ingestor returned no download URL");
            }
            return new DownloadUrlResult(body.getUrl(), body.getExpiresInSeconds() != null ? body.getExpiresInSeconds() : 900);
        } catch (HttpClientErrorException.NotFound e) {
            throw new java.util.NoSuchElementException("Document not found: " + documentId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new IllegalStateException("This document's original file is not available for download.");
        } catch (HttpClientErrorException e) {
            log.error("document-ingestor rejected internal download-url call for {}: {}", documentId, e.getStatusCode());
            throw new IllegalStateException("Could not resolve a download URL for this document.");
        }
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

    @Data
    private static class IngestorDownloadUrlResponse {
        private String url;
        private Integer expiresInSeconds;
        private String error;
    }
}
