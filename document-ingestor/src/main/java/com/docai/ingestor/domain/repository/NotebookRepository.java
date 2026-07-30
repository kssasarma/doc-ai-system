package com.docai.ingestor.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.ingestor.domain.entity.Notebook;

@Repository
public interface NotebookRepository extends JpaRepository<Notebook, UUID> {

    List<Notebook> findByTenantIdAndOwnerIdOrderByUpdatedAtDesc(UUID tenantId, UUID ownerId);

    Optional<Notebook> findByIdAndTenantIdAndOwnerId(UUID id, UUID tenantId, UUID ownerId);
}
