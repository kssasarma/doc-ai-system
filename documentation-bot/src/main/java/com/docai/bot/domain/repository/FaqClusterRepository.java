package com.docai.bot.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.FaqCluster;

@Repository
public interface FaqClusterRepository extends JpaRepository<FaqCluster, UUID> {

    List<FaqCluster> findByTenantIdAndProductAndPeriodStartGreaterThanEqualOrderByQueryCountDesc(
        UUID tenantId, String product, LocalDate since);

    /**
     * Rolling-window deduplication: finds all clusters for the given tenant+product+version
     * whose {@code period_end} falls within the rolling window (i.e. ≥ {@code since}). This
     * replaces the original exact-period match so a topic can't reappear simply because the
     * generation-period dates rolled forward into a new week.
     *
     * Explicit JPQL handles nullable product/version correctly — derived-method names generate
     * {@code = :param} which fails for nulls in PostgreSQL.
     */
    @Query("""
        SELECT c FROM FaqCluster c
        WHERE c.tenantId = :tenantId
          AND (:product IS NULL OR c.product = :product)
          AND (:version IS NULL OR c.version = :version)
          AND c.periodEnd >= :since
        ORDER BY c.periodEnd DESC
        """)
    List<FaqCluster> findRecentClusters(
        @Param("tenantId") UUID tenantId,
        @Param("product") String product,
        @Param("version") String version,
        @Param("since") LocalDate since);
}
