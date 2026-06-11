package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.ChunkAnnotation;

@Repository
public interface ChunkAnnotationRepository extends JpaRepository<ChunkAnnotation, UUID> {
    List<ChunkAnnotation> findByDocumentChunkIdOrderByCreatedAtAsc(UUID chunkId);
    Optional<ChunkAnnotation> findByIdAndUserId(UUID id, UUID userId);
}
