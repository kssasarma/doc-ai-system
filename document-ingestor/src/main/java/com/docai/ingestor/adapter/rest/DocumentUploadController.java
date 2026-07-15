package com.docai.ingestor.adapter.rest;

import java.security.DigestInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docai.ingestor.application.service.DocumentQuotaService;
import com.docai.ingestor.application.service.DocumentStorageService;
import com.docai.ingestor.application.service.FileHashing;
import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.repository.DocumentRepository;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DocumentUploadController {

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final DocumentStorageService documentStorageService;
    private final DocumentQuotaService documentQuotaService;

    // Must match exactly what DocumentParser has a real implementation for (PdfParser,
    // ChmParser, HtmlParser, PlainTextParser) — this allowlist previously only listed pdf/chm,
    // silently blocking html/htm/txt/md uploads the backend could already parse correctly.
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "chm", "html", "htm", "txt", "md");

    @PostMapping("/upload")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("product") String product,
            @RequestParam("version") String version,
            @RequestParam(value = "documentName", required = false) String documentName) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(DocumentResponse.error("File is empty"));
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String extension = getExtension(originalFilename).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest()
                .body(DocumentResponse.error("Unsupported file type. Allowed: " + ALLOWED_EXTENSIONS));
        }
        if (product == null || product.isBlank()) {
            return ResponseEntity.badRequest().body(DocumentResponse.error("Product is required"));
        }
        if (version == null || version.isBlank()) {
            return ResponseEntity.badRequest().body(DocumentResponse.error("Version is required"));
        }

        String docName = (documentName != null && !documentName.isBlank())
            ? documentName : stripExtension(originalFilename);

        try {
            UUID tenantId = TenantContext.get();
            documentQuotaService.checkQuota(tenantId);

            // Hash while streaming straight into storage — the file never touches this
            // container's disk, not even transiently.
            String storageKey;
            String fileHash;
            try (DigestInputStream digestStream = FileHashing.wrap(file.getInputStream())) {
                storageKey = documentStorageService.store(digestStream, originalFilename, tenantId.toString(), file.getSize());
                fileHash = FileHashing.hexOf(digestStream.getMessageDigest());
            }

            // Reject exact duplicate that is already successfully processed — scoped to this tenant,
            // since two different tenants uploading byte-identical files must not collide.
            if (documentRepository.existsByFileHashAndTenantIdAndStatus(fileHash, tenantId, IngestionStatus.COMPLETED)) {
                documentStorageService.delete(storageKey);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(DocumentResponse.error("This exact document has already been processed"));
            }

            // Reuse or create document record — scoped to this tenant
            Optional<Document> existing = documentRepository.findByFileHashAndTenantId(fileHash, tenantId);
            Document document;
            if (existing.isPresent()) {
                document = existing.get();
                if (document.getStorageKey() != null) {
                    // Replacing a stale (failed/pending) upload's file with this fresh one.
                    documentStorageService.delete(document.getStorageKey());
                }
                document.setStorageKey(storageKey);
                document.setStorageType(documentStorageService.storageType());
                document.setStatus(IngestionStatus.PROCESSING);
                document.setErrorMessage(null);
                document = documentRepository.save(document);
            } else {
                document = documentRepository.save(Document.builder()
                    .tenantId(tenantId)
                    .product(product.trim())
                    .version(version.trim())
                    .documentName(docName)
                    .storageKey(storageKey)
                    .storageType(documentStorageService.storageType())
                    .fileHash(fileHash)
                    .fileType(extension)
                    .status(IngestionStatus.PROCESSING)
                    .build());
            }

            ingestionService.ingestUploadedFile(document.getId());

            log.info("Upload accepted: {} ({} v{})", docName, product, version);
            return ResponseEntity.accepted().body(DocumentResponse.of(document, "Processing started"));

        } catch (com.docai.ingestor.application.service.TenantQuotaExceededException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(DocumentResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Upload processing error", e);
            return ResponseEntity.internalServerError()
                .body(DocumentResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/retrigger")
    public ResponseEntity<DocumentResponse> retrigger(@PathVariable UUID id) {
        UUID tenantId = TenantContext.get();
        boolean ownedByCaller = documentRepository.findById(id)
            .map(doc -> tenantId.equals(doc.getTenantId()))
            .orElse(false);
        if (!ownedByCaller) {
            return ResponseEntity.notFound().build();
        }
        try {
            // prepareRetrigger commits the PROCESSING state before async starts
            ingestionService.prepareRetrigger(id);
            // Start async processing after the transaction committed
            ingestionService.ingestUploadedFile(id);
            return documentRepository.findById(id)
                .map(doc -> ResponseEntity.ok(DocumentResponse.of(doc, "Re-processing started")))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(DocumentResponse.error(e.getMessage()));
        }
    }

    /** A time-limited direct-download link for a document's original file — ADMIN console use
     * (e.g. the Documents tab). End-user "open citation" access goes through the bot's
     * access-policy-gated proxy, which calls {@code InternalDocumentController} instead; this
     * endpoint requires ADMIN (see class-level {@code @PreAuthorize}) so it isn't a substitute
     * for that per-document ACL check. Only available for documents that still have a stored
     * file — legacy documents ingested before source files were retained post-success, or ones
     * whose file was otherwise cleaned up, have none; re-upload is the only way to get one back. */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(@PathVariable UUID id) {
        UUID tenantId = TenantContext.get();
        Document doc = documentRepository.findById(id)
            .filter(d -> tenantId.equals(d.getTenantId()))
            .orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (doc.getStorageKey() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(DownloadUrlResponse.builder()
                .error("This document's original file is not available for download. Re-upload to get a new copy.")
                .build());
        }
        try {
            String url = documentStorageService.presignedDownloadUrl(doc.getStorageKey(), Duration.ofMinutes(15));
            return ResponseEntity.ok(DownloadUrlResponse.builder().url(url).expiresInSeconds(900).build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(DownloadUrlResponse.builder().error(e.getMessage()).build());
        }
    }

    /** Retriggers every FAILED document for this tenant in one action — the per-document endpoint
     * above required opening each failed row individually, tedious after a bulk-upload batch hits
     * a transient issue (e.g. the embedding provider was briefly unavailable). Skips (rather than
     * fails the whole request over) documents that individually can't be retriggered — same
     * validation as the single-document path — reporting why in the response. */
    @PostMapping("/reprocess-failed")
    public ResponseEntity<BulkReprocessResponse> reprocessFailed() {
        UUID tenantId = TenantContext.get();
        List<Document> failed = documentRepository.findByTenantIdAndStatus(tenantId, IngestionStatus.FAILED);

        int started = 0;
        List<String> skipped = new ArrayList<>();
        for (Document doc : failed) {
            try {
                // prepareRetrigger commits the PROCESSING state before async starts
                ingestionService.prepareRetrigger(doc.getId());
                // Start async processing after the transaction committed
                ingestionService.ingestUploadedFile(doc.getId());
                started++;
            } catch (IllegalArgumentException | IllegalStateException e) {
                skipped.add(doc.getDocumentName() + ": " + e.getMessage());
            }
        }

        log.info("Bulk reprocess for tenant {}: {} of {} failed documents restarted", tenantId, started, failed.size());
        return ResponseEntity.ok(BulkReprocessResponse.builder()
            .totalFailed(failed.size())
            .started(started)
            .skipped(skipped)
            .build());
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<DocumentResponse>> getAllDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(documentRepository.searchByTenantId(TenantContext.get(), q, pageable)
            .map(doc -> DocumentResponse.of(doc, null)));
    }

    /** Removes a document outright — chunks and the stored source file are cleaned up alongside
     * the row (see IngestionService#deleteDocument). Any status, not just FAILED/QUARANTINED. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            ingestionService.deleteDocument(id, TenantContext.get());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    @Data
    @Builder
    public static class DownloadUrlResponse {
        private String url;
        private Integer expiresInSeconds;
        private String error;
    }

    @Data
    @Builder
    public static class BulkReprocessResponse {
        private int totalFailed;
        private int started;
        private List<String> skipped;
    }

    @Data
    @Builder
    public static class DocumentResponse {
        private String id;
        private String product;
        private String version;
        private String documentName;
        private String status;
        private Integer chunkCount;
        private String errorMessage;
        private String createdAt;
        private String message;
        private String error;

        public static DocumentResponse of(Document doc, String message) {
            return DocumentResponse.builder()
                .id(doc.getId().toString())
                .product(doc.getProduct())
                .version(doc.getVersion())
                .documentName(doc.getDocumentName())
                .status(doc.getStatus().name())
                .chunkCount(doc.getChunkCount())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                .message(message)
                .build();
        }

        public static DocumentResponse error(String errorMsg) {
            return DocumentResponse.builder().error(errorMsg).build();
        }
    }
}
