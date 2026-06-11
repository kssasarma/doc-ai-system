package com.docai.ingestor.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.ingestor.domain.entity.ConnectorSyncPage;

public interface ConnectorSyncPageRepository extends JpaRepository<ConnectorSyncPage, UUID> {
    List<ConnectorSyncPage> findByTokenId(UUID tokenId);
    Optional<ConnectorSyncPage> findByTokenIdAndExternalId(UUID tokenId, String externalId);
    long countByTokenIdAndSyncStatus(UUID tokenId, ConnectorSyncPage.SyncStatus status);
}
