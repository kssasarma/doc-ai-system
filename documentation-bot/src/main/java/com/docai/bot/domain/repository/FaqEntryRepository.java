package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
}
