package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.Notebook;

@Repository
public interface NotebookRepository extends JpaRepository<Notebook, UUID> {

    Optional<Notebook> findByIdAndTenantIdAndOwnerId(UUID id, UUID tenantId, UUID ownerId);
}
