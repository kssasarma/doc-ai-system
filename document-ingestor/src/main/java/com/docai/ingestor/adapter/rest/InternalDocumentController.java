package com.docai.ingestor.adapter.rest;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.ingestor.application.service.DocumentStorageService;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.repository.DocumentRepository;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server-to-server API for documentation-bot only — authenticated by
 * {@link com.docai.ingestor.config.InternalServiceAuthFilter}, not a user JWT. The bot has
 * already resolved whether the calling user may see this document (DocumentAccessPolicy) before
 * ever reaching this endpoint; the ingestor has no user/grant data of its own to re-check, so it
 * trusts a validly-signed request and just needs the tenant to match the document (defense in
 * depth in case a signature is somehow replayed against the wrong tenant's document).
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;

    @GetMapping("/{id}/download-url")
    public ResponseEntity<InternalDownloadUrlResponse> downloadUrl(@PathVariable UUID id) {
        Document doc = documentRepository.findById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (doc.getStorageKey() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(InternalDownloadUrlResponse.builder()
                    .error("This document's original file is not available for download.")
                    .build());
        }
        try {
            String url = documentStorageService.presignedDownloadUrl(doc.getStorageKey(), Duration.ofMinutes(15));
            return ResponseEntity.ok(InternalDownloadUrlResponse.builder()
                .url(url).expiresInSeconds(900).build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(InternalDownloadUrlResponse.builder().error(e.getMessage()).build());
        }
    }

    @Data
    @Builder
    public static class InternalDownloadUrlResponse {
        private String url;
        private Integer expiresInSeconds;
        private String error;
    }
}
