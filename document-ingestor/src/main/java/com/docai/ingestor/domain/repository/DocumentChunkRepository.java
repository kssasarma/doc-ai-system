package com.docai.ingestor.domain.repository;

import com.docai.ingestor.domain.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);
    
    @Modifying
    @Query("DELETE FROM DocumentChunk dc WHERE dc.documentId = :documentId")
    void deleteByDocumentId(UUID documentId);
    
    long countByDocumentId(UUID documentId);
}
