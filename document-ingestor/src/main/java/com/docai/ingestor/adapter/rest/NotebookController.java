package com.docai.ingestor.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.config.JwtTokenFilter.AdminPrincipal;
import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Notebook;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.NotebookRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Personal document libraries — any authenticated user (not just ADMIN, see SecurityConfig's
 * {@code /api/notebooks/**} matcher) can create their own notebook, upload documents into it,
 * and later chat scoped only to that notebook's contents (see documentation-bot's ChatService).
 * Every operation here is scoped to the caller's own (tenant, ownerId) — a notebook is never
 * visible or editable by anyone but the user who created it, including tenant ADMINs.
 */
@Slf4j
@RestController
@RequestMapping("/api/notebooks")
@RequiredArgsConstructor
public class NotebookController {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "chm", "html", "htm", "txt", "md");
    private static final String PLACEHOLDER_PRODUCT = "Personal Library";
    private static final String PLACEHOLDER_VERSION = "n/a";

    private final NotebookRepository notebookRepository;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    @GetMapping
    public ResponseEntity<List<NotebookResponse>> list(@AuthenticationPrincipal AdminPrincipal principal) {
        UUID tenantId = TenantContext.get();
        List<Notebook> notebooks =
            notebookRepository.findByTenantIdAndOwnerIdOrderByUpdatedAtDesc(tenantId, principal.userId());
        return ResponseEntity.ok(notebooks.stream().map(this::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<NotebookResponse> create(
            @Valid @RequestBody CreateNotebookRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        Notebook notebook = notebookRepository.save(Notebook.builder()
            .tenantId(TenantContext.get())
            .ownerId(principal.userId())
            .name(request.getName().trim())
            .description(request.getDescription())
            .build());
        return ResponseEntity.ok(toResponse(notebook));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal principal) {
        UUID tenantId = TenantContext.get();
        Notebook notebook = requireOwnedNotebook(id, tenantId, principal.userId());
        for (Document doc : documentRepository.findByNotebookIdOrderByCreatedAtDesc(notebook.getId())) {
            ingestionService.deleteDocument(doc.getId(), tenantId);
        }
        notebookRepository.delete(notebook);
        log.info("Deleted notebook {} and its documents for user {}", id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal principal) {
        Notebook notebook = requireOwnedNotebook(id, TenantContext.get(), principal.userId());
        return ResponseEntity.ok(documentRepository.findByNotebookIdOrderByCreatedAtDesc(notebook.getId())
            .stream().map(doc -> DocumentResponse.of(doc, null)).toList());
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentName", required = false) String documentName,
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID tenantId = TenantContext.get();
        Notebook notebook = requireOwnedNotebook(id, tenantId, principal.userId());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(DocumentResponse.error("File is empty"));
        }
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String extension = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest()
                .body(DocumentResponse.error("Unsupported file type. Allowed: " + ALLOWED_EXTENSIONS));
        }
        String docName = (documentName != null && !documentName.isBlank())
            ? documentName : stripExtension(originalFilename);

        try {
            Document document = ingestionService.uploadAndIngest(
                file, originalFilename, extension, PLACEHOLDER_PRODUCT, PLACEHOLDER_VERSION, docName,
                tenantId, principal.userId(), notebook.getId());

            log.info("Notebook upload accepted: {} (notebook {})", docName, notebook.getId());
            return ResponseEntity.accepted().body(DocumentResponse.of(document, "Processing started"));

        } catch (com.docai.ingestor.application.service.DuplicateDocumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(DocumentResponse.error(e.getMessage()));
        } catch (com.docai.ingestor.application.service.TenantQuotaExceededException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(DocumentResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Notebook upload processing error", e);
            return ResponseEntity.internalServerError()
                .body(DocumentResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id, @PathVariable UUID documentId,
            @AuthenticationPrincipal AdminPrincipal principal) {
        UUID tenantId = TenantContext.get();
        Notebook notebook = requireOwnedNotebook(id, tenantId, principal.userId());
        Document doc = documentRepository.findById(documentId)
            .filter(d -> notebook.getId().equals(d.getNotebookId()))
            .orElseThrow(() -> new IllegalArgumentException("Document not found in this notebook: " + documentId));
        ingestionService.deleteDocument(doc.getId(), tenantId);
        return ResponseEntity.noContent().build();
    }

    private Notebook requireOwnedNotebook(UUID id, UUID tenantId, UUID userId) {
        return notebookRepository.findByIdAndTenantIdAndOwnerId(id, tenantId, userId)
            .orElseThrow(() -> new AccessDeniedException("Notebook not found or not owned by you: " + id));
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    private NotebookResponse toResponse(Notebook notebook) {
        return NotebookResponse.builder()
            .id(notebook.getId().toString())
            .name(notebook.getName())
            .description(notebook.getDescription())
            .createdAt(notebook.getCreatedAt() != null ? notebook.getCreatedAt().toString() : null)
            .updatedAt(notebook.getUpdatedAt() != null ? notebook.getUpdatedAt().toString() : null)
            .documentCount(documentRepository.findByNotebookIdOrderByCreatedAtDesc(notebook.getId()).size())
            .build();
    }

    @Data
    static class CreateNotebookRequest {
        @NotBlank(message = "Name is required")
        private String name;
        private String description;
    }

    @Data
    @Builder
    public static class NotebookResponse {
        private String id;
        private String name;
        private String description;
        private String createdAt;
        private String updatedAt;
        private int documentCount;
    }

    @Data
    @Builder
    public static class DocumentResponse {
        private String id;
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
