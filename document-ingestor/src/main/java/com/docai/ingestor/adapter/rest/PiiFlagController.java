package com.docai.ingestor.adapter.rest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.config.JwtTokenFilter.AdminPrincipal;
import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.DocumentChunk;
import com.docai.ingestor.domain.entity.PiiFlag;
import com.docai.ingestor.domain.repository.DocumentChunkRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.PiiFlagRepository;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/** Admin review surface for PiiDetectionService's findings — see IngestionService, which runs
 * the scan on every ingested document. */
@RestController
@RequestMapping("/api/pii-flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PiiFlagController {

    private static final int PREVIEW_LENGTH = 400;

    private final PiiFlagRepository piiFlagRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final IngestionService ingestionService;

    @GetMapping
    public ResponseEntity<List<PiiFlag>> list(@RequestParam(defaultValue = "false") boolean reviewed) {
        return ResponseEntity.ok(
            piiFlagRepository.findByTenantIdAndReviewed(TenantContext.get(), reviewed));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<PiiFlag> review(@PathVariable UUID id, @RequestBody ReviewRequest request,
                                           @AuthenticationPrincipal AdminPrincipal principal) {
        PiiFlag flag = piiFlagRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("PII flag not found"));
        if (!TenantContext.get().equals(flag.getTenantId())) {
            throw new AccessDeniedException("You do not have access to this PII flag");
        }
        flag.setReviewed(true);
        flag.setActionTaken(request.getActionTaken());
        flag.setReviewedBy(principal.userId());
        flag.setReviewedAt(LocalDateTime.now());
        return ResponseEntity.ok(piiFlagRepository.save(flag));
    }

    /** Approves a quarantined document's PII findings — makes it searchable (see
     * IngestionService#releaseFromQuarantine) and marks every pending flag for that document
     * reviewed, since a document can't be "half quarantined" by one flag among several. */
    @PostMapping("/document/{documentId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID documentId,
                                         @AuthenticationPrincipal AdminPrincipal principal) {
        Document document = requireOwnedDocument(documentId);
        ingestionService.releaseFromQuarantine(documentId);
        markFlagsReviewed(piiFlagRepository.findByDocumentId(documentId), principal.userId(), "RELEASED");
        return ResponseEntity.noContent().build();
    }

    /** Rejects a quarantined document — deletes it outright (see IngestionService#rejectQuarantined;
     * document_chunks and pii_flags both cascade-delete at the DB level, so there's nothing left
     * to separately mark reviewed). */
    @PostMapping("/document/{documentId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID documentId) {
        requireOwnedDocument(documentId);
        ingestionService.rejectQuarantined(documentId);
        return ResponseEntity.noContent().build();
    }

    // ── Chunk-level review ───────────────────────────────────────────────────

    /** Lists a document's chunks for PII review, each with the PII flags raised against it (see
     * PiiDetectionService#scanChunksAndFlag) so an admin can decide which specific chunk to
     * reject, rather than only being able to act on the document as a whole. */
    @GetMapping("/document/{documentId}/chunks")
    public ResponseEntity<List<ChunkReviewResponse>> listChunks(@PathVariable UUID documentId) {
        requireOwnedDocument(documentId);
        List<PiiFlag> flags = piiFlagRepository.findByDocumentId(documentId);
        List<ChunkReviewResponse> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId).stream()
            .map(chunk -> toChunkReviewResponse(chunk, flags))
            .toList();
        return ResponseEntity.ok(chunks);
    }

    /** Excludes a single chunk from search over PII found in it — leaves the rest of the document
     * (and every other chunk) searchable, unlike quarantining/rejecting the whole document. */
    @PatchMapping("/chunk/{chunkId}/exclude")
    public ResponseEntity<ChunkReviewResponse> excludeChunk(@PathVariable UUID chunkId,
                                                             @RequestBody(required = false) ExcludeChunkRequest request,
                                                             @AuthenticationPrincipal AdminPrincipal principal) {
        DocumentChunk chunk = requireOwnedChunk(chunkId);
        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
            ? request.getReason() : "PII";
        DocumentChunk updated = ingestionService.excludeChunk(chunkId, reason);
        markFlagsReviewed(
            piiFlagRepository.findByDocumentChunkId(chunkId), principal.userId(), "EXCLUDED");
        return ResponseEntity.ok(toChunkReviewResponse(updated, piiFlagRepository.findByDocumentId(chunk.getDocumentId())));
    }

    /** Reinstates a previously excluded chunk, making it searchable again. */
    @PatchMapping("/chunk/{chunkId}/include")
    public ResponseEntity<ChunkReviewResponse> includeChunk(@PathVariable UUID chunkId) {
        DocumentChunk chunk = requireOwnedChunk(chunkId);
        DocumentChunk updated = ingestionService.includeChunk(chunkId);
        return ResponseEntity.ok(toChunkReviewResponse(updated, piiFlagRepository.findByDocumentId(chunk.getDocumentId())));
    }

    private Document requireOwnedDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (!TenantContext.get().equals(document.getTenantId())) {
            throw new AccessDeniedException("You do not have access to this document");
        }
        return document;
    }

    private DocumentChunk requireOwnedChunk(UUID chunkId) {
        DocumentChunk chunk = chunkRepository.findById(chunkId)
            .orElseThrow(() -> new IllegalArgumentException("Chunk not found: " + chunkId));
        requireOwnedDocument(chunk.getDocumentId());
        return chunk;
    }

    private ChunkReviewResponse toChunkReviewResponse(DocumentChunk chunk, List<PiiFlag> documentFlags) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        String preview = content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content;
        List<PiiFlag> chunkFlags = documentFlags.stream()
            .filter(f -> chunk.getId().equals(f.getDocumentChunkId()))
            .toList();
        return ChunkReviewResponse.builder()
            .id(chunk.getId())
            .chunkIndex(chunk.getChunkIndex())
            .chunkType(chunk.getChunkType())
            .isLeaf(chunk.isLeaf())
            .contentPreview(preview)
            .excludedFromSearch(chunk.isExcludedFromSearch())
            .exclusionReason(chunk.getExclusionReason())
            .piiFlags(chunkFlags)
            .build();
    }

    private void markFlagsReviewed(List<PiiFlag> flags, UUID reviewerId, String actionTaken) {
        LocalDateTime now = LocalDateTime.now();
        for (PiiFlag flag : flags) {
            if (flag.isReviewed()) continue;
            flag.setReviewed(true);
            flag.setActionTaken(actionTaken);
            flag.setReviewedBy(reviewerId);
            flag.setReviewedAt(now);
        }
        piiFlagRepository.saveAll(flags);
    }

    @Data
    static class ReviewRequest {
        @NotBlank(message = "actionTaken is required")
        private String actionTaken; // e.g. ACKNOWLEDGED, REDACTED, DISMISSED
    }

    @Data
    static class ExcludeChunkRequest {
        private String reason; // defaults to "PII" when blank
    }

    @Data
    @Builder
    static class ChunkReviewResponse {
        private UUID id;
        private Integer chunkIndex;
        private String chunkType;
        private boolean isLeaf;
        private String contentPreview;
        private boolean excludedFromSearch;
        private String exclusionReason;
        private List<PiiFlag> piiFlags;
    }
}
