package com.docai.ingestor.adapter.rest;

import java.security.DigestInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "chm");

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

            // Hash while streaming straight into storage — the file never touches this
            // container's disk, not even transiently.
            String storageKey;
            String fileHash;
            try (DigestInputStream digestStream = FileHashing.wrap(file.getInputStream())) {
                storageKey = documentStorageService.store(digestStream, originalFilename, tenantId.toString());
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

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        return ResponseEntity.ok(documentRepository.findByTenantId(TenantContext.get()).stream()
            .map(doc -> DocumentResponse.of(doc, null))
            .toList());
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
