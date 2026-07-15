package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only entity for querying documents from the ingestor database
 */
@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private String version;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    /** The embedding model that actually produced this document's chunk embeddings (see
     * document-ingestor's EmbeddingService) — used by VectorSearchService to request a matching
     * query embedding rather than blindly the tenant's *current* embedding config. Null for
     * documents ingested before this column existed. */
    @Column(name = "embedding_model")
    private String embeddingModel;

    /** Mirrors the ingestor-owned {@code IngestionStatus} enum as a plain string — this entity
     * only reads the shared table, so a raw string comparison (see DocumentRepository) is enough;
     * no need to duplicate the enum type here. PENDING | PROCESSING | COMPLETED | FAILED. */
    @Column(nullable = false)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
