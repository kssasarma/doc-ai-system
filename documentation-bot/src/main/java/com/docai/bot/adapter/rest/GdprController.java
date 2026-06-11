package com.docai.bot.adapter.rest;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.DataExportService;
import com.docai.bot.application.service.UserService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.GdprDeletionRequest;
import com.docai.bot.domain.repository.GdprDeletionRequestRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user/gdpr")
@RequiredArgsConstructor
public class GdprController {

    private final DataExportService exportService;
    private final UserService userService;
    private final GdprDeletionRequestRepository deletionRequestRepository;

    /** GDPR Article 20 — export everything we hold about the authenticated user. */
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportMyData() {
        UUID userId = userService.currentUserId();
        String json = exportService.exportUserData(userId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"my-data-" + userId + ".json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.getBytes());
    }

    /** GDPR Article 17 — right to erasure. Creates a deletion request processed asynchronously. */
    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> requestDeletion() {
        UUID userId   = userService.currentUserId();
        UUID tenantId = TenantContext.get();
        deletionRequestRepository.save(GdprDeletionRequest.builder()
            .userId(userId)
            .tenantId(tenantId)
            .status("PENDING")
            .build());
        return ResponseEntity.accepted().build();
    }

    /** Admin: list pending deletion requests for current tenant. */
    @GetMapping("/admin/deletion-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listDeletionRequests() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(
            deletionRequestRepository.findByTenantIdAndStatus(tenantId, "PENDING"));
    }
}
