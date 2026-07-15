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

    /** A hybrid-retrieval candidate — carries the raw embedding text (not a similarity score)
     * because {@link com.docai.bot.application.service.VectorSearchService} recomputes cosine
     * similarity uniformly, in Java, for every candidate from both the dense and lexical paths
     * (see its class doc) rather than trusting two different scoring scales. */
    interface HybridCandidate {
        String getChunkId();
        String getContent();
        String getDocumentId();
        String getDocumentName();
        String getProduct();
        String getVersion();
        String getEmbeddingText();
    }

    // Phase 2/6/7 — the sole eligibility-gated queries: only documents in this tenant that appear
    // in documentIds (the caller's resolved SearchScope) are ever searchable. Callers must not
    // pass an empty documentIds — short-circuit before calling (see VectorSearchService).
    // narrowProduct/narrowVersion (Phase 7) are an optional, opt-in narrow on top of that gate —
    // pass null for either to leave it unfiltered. The CAST(... AS text) is required for
    // PostgreSQL/JDBC to type-infer a null-valued bind parameter correctly.
    //
    // Widened dense (cosine-similarity) candidate pool — the first half of hybrid retrieval.
    @Query(value = """
        SELECT CAST(COALESCE(parent.id, dc.id) AS text) AS chunkId,
               COALESCE(parent.content, dc.content)      AS content,
               CAST(d.id AS text)                        AS documentId,
               d.document_name                           AS documentName,
               d.product                                  AS product,
               d.version                                  AS version,
               CAST(dc.embedding AS text)                 AS embeddingText
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        LEFT JOIN document_chunks parent ON dc.parent_chunk_id = parent.id
        WHERE d.tenant_id = :tenantId
          AND d.id IN (:documentIds)
          AND d.status = 'COMPLETED'
          AND (dc.is_leaf = TRUE OR dc.is_leaf IS NULL)
          AND (CAST(:narrowProduct AS text) IS NULL OR d.product = :narrowProduct)
          AND (CAST(:narrowVersion AS text) IS NULL OR d.version = :narrowVersion)
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<HybridCandidate> findTopNDenseAccessible(UUID tenantId, Collection<UUID> documentIds, String embedding,
                                                   String narrowProduct, String narrowVersion, int limit);

    // Widened lexical (full-text) candidate pool — the second half of hybrid retrieval. Matches
    // against the leaf chunk's own tsvector (mirroring the dense query matching the leaf's own
    // embedding) but still surfaces the richer parent section content, same as the dense path.
    @Query(value = """
        SELECT CAST(COALESCE(parent.id, dc.id) AS text) AS chunkId,
               COALESCE(parent.content, dc.content)      AS content,
               CAST(d.id AS text)                        AS documentId,
               d.document_name                           AS documentName,
               d.product                                  AS product,
               d.version                                  AS version,
               CAST(dc.embedding AS text)                 AS embeddingText
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        LEFT JOIN document_chunks parent ON dc.parent_chunk_id = parent.id
        WHERE d.tenant_id = :tenantId
          AND d.id IN (:documentIds)
          AND d.status = 'COMPLETED'
          AND (dc.is_leaf = TRUE OR dc.is_leaf IS NULL)
          AND (CAST(:narrowProduct AS text) IS NULL OR d.product = :narrowProduct)
          AND (CAST(:narrowVersion AS text) IS NULL OR d.version = :narrowVersion)
          AND dc.search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(dc.search_vector, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<HybridCandidate> findTopNLexicalAccessible(UUID tenantId, Collection<UUID> documentIds, String query,
                                                      String narrowProduct, String narrowVersion, int limit);
}
