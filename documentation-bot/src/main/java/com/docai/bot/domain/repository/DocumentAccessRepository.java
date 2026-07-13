package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.DocumentAccess;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    Optional<DocumentAccess> findByDocumentIdAndUserId(UUID documentId, UUID userId);

    List<DocumentAccess> findByDocumentIdAndTenantId(UUID documentId, UUID tenantId);

    @Query("SELECT da.documentId FROM DocumentAccess da WHERE da.userId = :userId AND da.tenantId = :tenantId")
    Set<UUID> findAccessibleDocumentIds(UUID userId, UUID tenantId);

    void deleteByDocumentIdAndUserId(UUID documentId, UUID userId);

    void deleteByUserId(UUID userId);
}
