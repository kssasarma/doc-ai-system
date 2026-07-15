package com.docai.bot.adapter.rest;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.InternalDocumentDownloadService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.model.SearchScope;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/** Backs "open citation" in the chat UI (Phase 6.3) — any USER, not just ADMIN, provided the
 * document is within their own resolved {@link SearchScope}. That scope is exactly what already
 * gated the citation being shown to them in the first place, so this re-check only matters if
 * access was revoked since the message was sent. */
@RestController
@RequiredArgsConstructor
public class DocumentDownloadController {

    private final DocumentAccessPolicy documentAccessPolicy;
    private final InternalDocumentDownloadService internalDocumentDownloadService;

    @GetMapping("/api/documents/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        SearchScope scope = documentAccessPolicy.resolveScope(principal.userId(), TenantContext.get());
        if (!scope.documentIds().contains(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                DownloadUrlResponse.builder().error("You do not have access to this document.").build());
        }
        try {
            InternalDocumentDownloadService.DownloadUrlResult result = internalDocumentDownloadService.resolveDownloadUrl(id);
            return ResponseEntity.ok(DownloadUrlResponse.builder()
                .url(result.url()).expiresInSeconds(result.expiresInSeconds()).build());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(DownloadUrlResponse.builder().error(e.getMessage()).build());
        }
    }

    @Data
    @Builder
    public static class DownloadUrlResponse {
        private String url;
        private Integer expiresInSeconds;
        private String error;
    }
}
