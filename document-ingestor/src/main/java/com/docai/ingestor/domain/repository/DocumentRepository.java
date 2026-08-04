package com.docai.ingestor.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByFileHash(String fileHash);

    Optional<Document> findByFileHashAndTenantId(String fileHash, UUID tenantId);

    List<Document> findByProductAndVersion(String product, String version);

    List<Document> findByStatus(IngestionStatus status);

    List<Document> findByTenantId(UUID tenantId);

    List<Document> findByTenantIdAndStatus(UUID tenantId, IngestionStatus status);

    /** Excludes FAILED — a failed upload shouldn't permanently eat into the tenant's plan quota. */
    long countByTenantIdAndStatusNot(UUID tenantId, IngestionStatus status);

    boolean existsByFileHash(String fileHash);

    boolean existsByFileHashAndStatus(String fileHash, IngestionStatus status);

    boolean existsByFileHashAndTenantIdAndStatus(String fileHash, UUID tenantId, IngestionStatus status);

    /** Notebook-scoped dedup check — {@code notebookId} null means the tenant-wide admin corpus
     * bucket, matching only other admin documents; a real notebookId matches only that one
     * notebook. Without this split, an identical file already sitting in one user's private
     * notebook (or the admin corpus) could be silently "reused" as if it were part of a
     * completely unrelated bucket. */
    @Query("SELECT COUNT(d) > 0 FROM Document d WHERE d.fileHash = :fileHash AND d.tenantId = :tenantId " +
           "AND ((:notebookId IS NULL AND d.notebookId IS NULL) OR d.notebookId = :notebookId) " +
           "AND d.status = :status")
    boolean existsByFileHashAndTenantIdAndNotebookIdAndStatus(
        String fileHash, UUID tenantId, UUID notebookId, IngestionStatus status);

    /** Notebook-scoped reuse-or-create lookup — see {@link #existsByFileHashAndTenantIdAndNotebookIdAndStatus}. */
    @Query("SELECT d FROM Document d WHERE d.fileHash = :fileHash AND d.tenantId = :tenantId " +
           "AND ((:notebookId IS NULL AND d.notebookId IS NULL) OR d.notebookId = :notebookId)")
    Optional<Document> findByFileHashAndTenantIdAndNotebookId(String fileHash, UUID tenantId, UUID notebookId);

    /** Documents in one personal notebook — used both to list the notebook's contents and to
     * cascade-delete them when the notebook itself is deleted. */
    List<Document> findByNotebookIdOrderByCreatedAtDesc(UUID notebookId);

    /** Used at ingestion-completion time to find prior COMPLETED documents for the same
     * (tenant, product, version) that the newly-completed one supersedes. Excludes the new
     * document itself by id. */
    List<Document> findByTenantIdAndProductAndVersionAndStatusAndIdNot(
        UUID tenantId, String product, String version, IngestionStatus status, UUID id);

    /** Reaper query: documents stuck in PROCESSING (e.g. the pod died mid-ingestion) whose last
     * update predates the timeout — never recovered on their own since nothing else watches them. */
    List<Document> findByStatusAndUpdatedAtBefore(IngestionStatus status, LocalDateTime cutoff);

    // Phase 6.4 — paginated, searchable admin documents table. Ordered by product/version so a
    // given page groups reasonably well client-side even though pagination and "group by product"
    // are in some tension at a page boundary.
    @Query("""
        SELECT d FROM Document d
        WHERE d.tenantId = :tenantId
          AND (:q IS NULL OR :q = '' OR LOWER(d.documentName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(d.product) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY d.product ASC, d.version DESC, d.documentName ASC
        """)
    Page<Document> searchByTenantId(UUID tenantId, String q, Pageable pageable);
}
