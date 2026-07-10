package com.docai.ingestor.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_product_version", columnList = "product,version"),
    @Index(name = "idx_file_hash", columnList = "file_hash")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String product;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    /** @deprecated legacy local-disk path — no longer populated for new documents, only
     * historical rows. See {@link #storageKey} / {@link #storageType}. */
    @Deprecated
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /** Key used to resolve/delete this file via {@code DocumentStorageService}. Null once the
     * file has been deleted post-ingestion (see {@code IngestionService#processDocument}). */
    @Column(name = "storage_key", length = 1000)
    private String storageKey;

    /** Which {@code DocumentStorageService} implementation stored this file (e.g. "S3"). */
    @Column(name = "storage_type", length = 20)
    private String storageType;

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum IngestionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
