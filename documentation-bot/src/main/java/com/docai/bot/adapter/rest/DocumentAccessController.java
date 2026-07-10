package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.application.service.DocumentAccessService;
import com.docai.bot.application.service.DocumentAccessService.GranteeDTO;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Tenant-scoped, ADMIN-only management of per-document access grants. A tenant admin uploads a
 * document via document-ingestor, then grants specific tenant users access to it here — the two
 * are separate calls by design (document-ingestor owns ingestion; access control lives alongside
 * User/Tenant/retrieval here, where it's actually enforced).
 */
@RestController
@RequestMapping("/api/documents/{documentId}/access")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DocumentAccessController {

    private final DocumentAccessService documentAccessService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<GranteeDTO>> listGrantees(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentAccessService.listGrantees(documentId, TenantContext.get()));
    }

    @PostMapping
    public ResponseEntity<GranteeDTO> grant(@PathVariable UUID documentId,
                                             @Valid @RequestBody GrantRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.get();
        GranteeDTO grant = documentAccessService.grant(documentId, request.getUserId(), tenantId, principal.userId());
        auditLogService.log(principal.userId(), "DOCUMENT_ACCESS_GRANT", "DOCUMENT",
            documentId, "user=" + request.getUserId(), null);
        return ResponseEntity.ok(grant);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID documentId,
                                        @PathVariable UUID userId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        documentAccessService.revoke(documentId, userId, TenantContext.get());
        auditLogService.log(principal.userId(), "DOCUMENT_ACCESS_REVOKE", "DOCUMENT",
            documentId, "user=" + userId, null);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class GrantRequest {
        @NotNull
        private UUID userId;
    }
}
