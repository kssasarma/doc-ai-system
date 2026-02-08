package com.docai.ingestor.adapter.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.repository.DocumentRepository;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final DocumentRepository documentRepository;

    @PostMapping("/reload")
    public ResponseEntity<ApiResponse> reload() {
        // Trigger re-scan of directory
        // This is handled by DirectoryWatcher on startup
        return ResponseEntity.ok(
            ApiResponse.builder()
                .success(true)
                .message("Reload triggered")
                .build()
        );
    }

    @GetMapping("/status")
    public ResponseEntity<IngestionStatus> getStatus() {
        List<Document> allDocs = documentRepository.findAll();
        
        long completed = allDocs.stream()
            .filter(d -> d.getStatus() == Document.IngestionStatus.COMPLETED)
            .count();
        long processing = allDocs.stream()
            .filter(d -> d.getStatus() == Document.IngestionStatus.PROCESSING)
            .count();
        long failed = allDocs.stream()
            .filter(d -> d.getStatus() == Document.IngestionStatus.FAILED)
            .count();
        long pending = allDocs.stream()
            .filter(d -> d.getStatus() == Document.IngestionStatus.PENDING)
            .count();

        long totalChunks = allDocs.stream()
            .mapToLong(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
            .sum();

        return ResponseEntity.ok(
            IngestionStatus.builder()
                .totalDocuments(allDocs.size())
                .completed(completed)
                .processing(processing)
                .failed(failed)
                .pending(pending)
                .totalChunks(totalChunks)
                .build()
        );
    }

    @GetMapping("/documents")
    public ResponseEntity<List<DocumentInfo>> getAllDocuments() {
        List<Document> documents = documentRepository.findAll();
        
        List<DocumentInfo> docInfos = documents.stream()
            .map(doc -> DocumentInfo.builder()
                .id(doc.getId().toString())
                .product(doc.getProduct())
                .version(doc.getVersion())
                .documentName(doc.getDocumentName())
                .status(doc.getStatus().name())
                .chunkCount(doc.getChunkCount())
                .createdAt(doc.getCreatedAt().toString())
                .build()
            )
            .toList();

        return ResponseEntity.ok(docInfos);
    }

    @Data
    @Builder
    static class ApiResponse {
        private boolean success;
        private String message;
    }

    @Data
    @Builder
    static class IngestionStatus {
        private long totalDocuments;
        private long completed;
        private long processing;
        private long failed;
        private long pending;
        private long totalChunks;
    }

    @Data
    @Builder
    static class DocumentInfo {
        private String id;
        private String product;
        private String version;
        private String documentName;
        private String status;
        private Integer chunkCount;
        private String createdAt;
    }
}
