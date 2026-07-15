package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Phase 6.8 — actor filter added alongside the existing action/since ones. A single flexible
    // query (each filter optional via CAST(... AS ...) IS NULL) replaces what would otherwise be
    // 2^3 = 8 combinatorial finder methods for three independent optional filters.
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.tenantId = :tenantId
          AND (:action IS NULL OR a.action = :action)
          AND (:since IS NULL OR a.createdAt > :since)
          AND (:actorId IS NULL OR a.actorId = :actorId)
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> search(UUID tenantId, String action, LocalDateTime since, UUID actorId, Pageable pageable);
}
