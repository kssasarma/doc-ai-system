package com.docai.ingestor.adapter.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.ingestor.application.service.WebhookIngestionService;
import com.docai.ingestor.domain.entity.WebhookEvent;
import com.docai.ingestor.domain.repository.WebhookEventRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook endpoint for CI/CD-triggered document ingestion.
 * Authenticated via the standard JWT/API-key mechanism provided by the security filter chain.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookIngestionService webhookIngestionService;
    private final WebhookEventRepository webhookEventRepository;

    /**
     * POST /api/v1/ingest/webhook
     *
     * Body:
     * {
     *   "downloadUrl": "https://cdn.example.com/case360-23.5-install-guide.pdf",
     *   "product":     "case360",
     *   "version":     "23.5",
     *   "documentName": "Installation Guide"   (optional)
     * }
     *
     * Returns: { "jobId": "<uuid>", "status": "PENDING" }
     */
    @PostMapping("/webhook")
    public ResponseEntity<WebhookJobResponse> triggerWebhookIngestion(
            @Valid @RequestBody WebhookRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String requestedBy = extractRequestedBy(apiKeyHeader, authHeader);
        log.info("Webhook ingestion triggered by '{}': {} {} {}", requestedBy,
            request.getProduct(), request.getVersion(), request.getDownloadUrl());

        WebhookEvent event = webhookIngestionService.createEvent(
            request.getDownloadUrl(),
            request.getProduct(),
            request.getVersion(),
            request.getDocumentName(),
            requestedBy
        );

        // Kick off async processing immediately
        webhookIngestionService.processEvent(event.getId());

        return ResponseEntity.accepted().body(WebhookJobResponse.builder()
            .jobId(event.getId().toString())
            .status(event.getStatus().name())
            .message("Ingestion queued. Poll /api/v1/ingest/webhook/" + event.getId() + " for status.")
            .build());
    }

    /**
     * GET /api/v1/ingest/webhook/{jobId}
     * Poll for status of a webhook ingestion job.
     */
    @GetMapping("/webhook/{jobId}")
    public ResponseEntity<?> getWebhookStatus(@PathVariable UUID jobId) {
        return webhookEventRepository.findById(jobId)
            .map(event -> ResponseEntity.ok(WebhookStatusResponse.builder()
                .jobId(event.getId().toString())
                .status(event.getStatus().name())
                .product(event.getProduct())
                .version(event.getVersion())
                .documentName(event.getDocumentName())
                .documentId(event.getDocumentId() != null ? event.getDocumentId().toString() : null)
                .errorMessage(event.getErrorMessage())
                .retryCount(event.getRetryCount())
                .createdAt(event.getCreatedAt() != null ? event.getCreatedAt().toString() : null)
                .processedAt(event.getProcessedAt() != null ? event.getProcessedAt().toString() : null)
                .build()))
            .orElse(ResponseEntity.notFound().build());
    }

    private String extractRequestedBy(String apiKeyHeader, String authHeader) {
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.substring(0, Math.min(12, apiKeyHeader.length())) + "…";
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) return "jwt";
        if (authHeader != null && authHeader.startsWith("ApiKey ")) {
            String key = authHeader.substring(7);
            return key.substring(0, Math.min(12, key.length())) + "…";
        }
        return "unknown";
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    static class WebhookRequest {
        @NotBlank(message = "downloadUrl is required")
        private String downloadUrl;

        @NotBlank(message = "product is required")
        private String product;

        @NotBlank(message = "version is required")
        private String version;

        private String documentName;
    }

    @Data @Builder
    static class WebhookJobResponse {
        private String jobId;
        private String status;
        private String message;
    }

    @Data @Builder
    static class WebhookStatusResponse {
        private String jobId;
        private String status;
        private String product;
        private String version;
        private String documentName;
        private String documentId;
        private String errorMessage;
        private int retryCount;
        private String createdAt;
        private String processedAt;
    }
}
