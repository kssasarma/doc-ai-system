package com.docai.bot.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.DocumentChunk;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    
    @Query(value = "SELECT dc.*, d.product, d.version, d.document_name " +
                   "FROM document_chunks dc " +
                   "JOIN documents d ON dc.document_id = d.id " +
                   "WHERE d.product = :product AND d.version = :version " +
                   "ORDER BY dc.embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopKSimilar(String product, String version, String embedding, int limit);
    
    @Query(value = "SELECT dc.*, d.product, d.version, d.document_name " +
                   "FROM document_chunks dc " +
                   "JOIN documents d ON dc.document_id = d.id " +
                   "WHERE d.product = :product " +
                   "ORDER BY dc.embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopKSimilarByProduct(String product, String embedding, int limit);
    
    @Query(value = "SELECT dc.*, d.product, d.version, d.document_name " +
                   "FROM document_chunks dc " +
                   "JOIN documents d ON dc.document_id = d.id " +
                   "ORDER BY dc.embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopKSimilarAll(String embedding, int limit);
}
