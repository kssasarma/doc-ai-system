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

    List<Document> findByTenantId(UUID tenantId);

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT d.id FROM Document d WHERE d.tenantId = :tenantId")
    Set<UUID> findIdsByTenantId(UUID tenantId);

    @Query("SELECT DISTINCT d.product FROM Document d ORDER BY d.product")
    List<String> findDistinctProducts();

    /** Unordered — callers needing "latest" must sort with {@link com.docai.bot.domain.model.VersionComparator}
     * rather than trusting this query's order, which is a plain (lexicographic) SQL string sort. */
    @Query("SELECT DISTINCT d.version FROM Document d WHERE d.product = :product")
    List<String> findVersionsByProduct(String product);

    interface ProductVersion {
        String getProduct();
        String getVersion();
    }

    /** Distinct product+version pairs the caller can actually search — used to populate the
     * chat UI's scope picker and any other product/version enumeration that must not leak the
     * existence of documents outside the caller's tenant or access grants. Callers must not pass
     * an empty {@code documentIds} (JPQL's {@code IN ()} on an empty collection is undefined
     * behavior across JPA providers) — short-circuit before calling, same convention as
     * {@link com.docai.bot.application.service.VectorSearchService}. */
    @Query("SELECT DISTINCT d.product AS product, d.version AS version FROM Document d " +
           "WHERE d.tenantId = :tenantId AND d.id IN :documentIds")
    List<ProductVersion> findDistinctProductVersionsAccessible(UUID tenantId, Set<UUID> documentIds);
}
