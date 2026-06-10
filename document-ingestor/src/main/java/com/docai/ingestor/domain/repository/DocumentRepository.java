package com.docai.ingestor.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByFileHash(String fileHash);

    List<Document> findByProductAndVersion(String product, String version);

    List<Document> findByStatus(IngestionStatus status);

    boolean existsByFileHash(String fileHash);

    boolean existsByFileHashAndStatus(String fileHash, IngestionStatus status);
}
