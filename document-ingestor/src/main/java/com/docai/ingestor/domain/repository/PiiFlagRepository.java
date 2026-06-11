package com.docai.ingestor.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.ingestor.domain.entity.PiiFlag;

public interface PiiFlagRepository extends JpaRepository<PiiFlag, UUID> {

    List<PiiFlag> findByDocumentId(UUID documentId);

    List<PiiFlag> findByTenantIdAndReviewed(UUID tenantId, boolean reviewed);
}
