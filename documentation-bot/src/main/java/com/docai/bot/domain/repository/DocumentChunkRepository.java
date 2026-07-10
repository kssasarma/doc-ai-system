package com.docai.bot.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.DocumentChunk;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    interface ChunkSearchResult {
        String getChunkId();
        String getContent();
        String getDocumentName();
        String getProduct();
        String getVersion();
        double getSimilarity();
    }

    // Phase 6.7: Small-to-big retrieval — search leaf chunks, but if the leaf has a parent
    // section chunk, return the parent's (richer) content instead.
    @Query(value = """
        SELECT CAST(COALESCE(parent.id, dc.id) AS text)          AS chunkId,
               COALESCE(parent.content, dc.content)              AS content,
               d.document_name                                   AS documentName,
               d.product                                         AS product,
               d.version                                         AS version,
               (1 - (dc.embedding <=> CAST(:embedding AS vector))) AS similarity
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        LEFT JOIN document_chunks parent ON dc.parent_chunk_id = parent.id
        WHERE d.product = :product AND d.version = :version
          AND (dc.is_leaf = TRUE OR dc.is_leaf IS NULL)
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSearchResult> findTopKSimilar(String product, String version, String embedding, int limit);

    @Query(value = """
        SELECT CAST(COALESCE(parent.id, dc.id) AS text)          AS chunkId,
               COALESCE(parent.content, dc.content)              AS content,
               d.document_name                                   AS documentName,
               d.product                                         AS product,
               d.version                                         AS version,
               (1 - (dc.embedding <=> CAST(:embedding AS vector))) AS similarity
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        LEFT JOIN document_chunks parent ON dc.parent_chunk_id = parent.id
        WHERE d.product = :product
          AND (dc.is_leaf = TRUE OR dc.is_leaf IS NULL)
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSearchResult> findTopKSimilarByProduct(String product, String embedding, int limit);

    @Query(value = """
        SELECT CAST(COALESCE(parent.id, dc.id) AS text)          AS chunkId,
               COALESCE(parent.content, dc.content)              AS content,
               d.document_name                                   AS documentName,
               d.product                                         AS product,
               d.version                                         AS version,
               (1 - (dc.embedding <=> CAST(:embedding AS vector))) AS similarity
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        LEFT JOIN document_chunks parent ON dc.parent_chunk_id = parent.id
        WHERE dc.is_leaf = TRUE OR dc.is_leaf IS NULL
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSearchResult> findTopKSimilarAll(String embedding, int limit);

    // Phase 2 — the sole eligibility-gated query: only documents in this tenant that appear in
    // documentIds (the caller's resolved SearchScope) are ever searchable. Callers must not pass
    // an empty documentIds — short-circuit before calling (see VectorSearchService).
    @Query(value = """
        SELECT CAST(COALESCE(parent.id, dc.id) AS text)          AS chunkId,
               COALESCE(parent.content, dc.content)              AS content,
               d.document_name                                   AS documentName,
               d.product                                         AS product,
               d.version                                         AS version,
               (1 - (dc.embedding <=> CAST(:embedding AS vector))) AS similarity
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        LEFT JOIN document_chunks parent ON dc.parent_chunk_id = parent.id
        WHERE d.tenant_id = :tenantId
          AND d.id IN (:documentIds)
          AND (dc.is_leaf = TRUE OR dc.is_leaf IS NULL)
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSearchResult> findTopKSimilarAccessible(UUID tenantId, Collection<UUID> documentIds, String embedding, int limit);
}
