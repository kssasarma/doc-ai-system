package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByProductAndVersion(String product, String version);

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT d.id FROM Document d WHERE d.tenantId = :tenantId")
    Set<UUID> findIdsByTenantId(UUID tenantId);

    @Query("SELECT DISTINCT d.product FROM Document d ORDER BY d.product")
    List<String> findDistinctProducts();

    @Query("SELECT DISTINCT d.version FROM Document d WHERE d.product = :product ORDER BY d.version")
    List<String> findVersionsByProduct(String product);
}
