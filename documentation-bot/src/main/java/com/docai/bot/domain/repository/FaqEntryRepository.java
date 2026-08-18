package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.FaqEntry;
import com.docai.bot.domain.entity.FaqEntry.Status;

@Repository
public interface FaqEntryRepository extends JpaRepository<FaqEntry, UUID> {

    Page<FaqEntry> findByTenantIdAndStatus(UUID tenantId, Status status, Pageable pageable);

    Page<FaqEntry> findByTenantIdAndProductAndVersionAndStatus(
        UUID tenantId, String product, String version, Status status, Pageable pageable);

    Page<FaqEntry> findByTenantIdAndProductAndStatus(
        UUID tenantId, String product, Status status, Pageable pageable);

    /** Also tenant-scoped: {@code findById} on its own would let a caller who already knows an
     *  id from another tenant read/act on it, since JPA's generated finder has no such filter. */
    Optional<FaqEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.viewCount = f.viewCount + 1 WHERE f.id = :id")
    void incrementViewCount(UUID id);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.helpfulCount = f.helpfulCount + 1 WHERE f.id = :id")
    void incrementHelpfulCount(UUID id);

    /**
     * Rejection-aware dedup: finds REJECTED entries for a tenant+product+version within the
     * rolling window. Explicit JPQL handles nullable product/version correctly (IS NULL vs = ?)
     * — JPA derived-method names generate {@code = :param} which fails for nulls in PostgreSQL.
     */
    @Query("""
        SELECT f FROM FaqEntry f
        WHERE f.tenantId = :tenantId
          AND (:product IS NULL OR f.product = :product)
          AND (:version IS NULL OR f.version = :version)
          AND f.status = com.docai.bot.domain.entity.FaqEntry.Status.REJECTED
          AND f.createdAt > :since
        """)
    List<FaqEntry> findRejectedInWindow(
        @Param("tenantId") UUID tenantId,
        @Param("product") String product,
        @Param("version") String version,
        @Param("since") LocalDateTime since);

    /**
     * Helpfulness feedback loop: finds all APPROVED entries with at least {@code minViews} views
     * so {@link com.docai.bot.application.service.FaqMaintenanceService} can compute the
     * helpfulness rate and flag stale entries for re-review. No tenant filter — the maintenance
     * job sweeps all tenants (same as the weekly cron), and the partial index
     * {@code idx_faq_entries_approved_views} keeps this efficient.
     */
    @Query("SELECT f FROM FaqEntry f WHERE f.status = 'APPROVED' AND f.viewCount >= :minViews")
    List<FaqEntry> findApprovedWithMinViews(@Param("minViews") int minViews);
}
