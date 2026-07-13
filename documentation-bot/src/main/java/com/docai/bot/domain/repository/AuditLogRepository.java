package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndActionOrderByCreatedAtDesc(UUID tenantId, String action, Pageable pageable);

    Page<AuditLog> findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(
        UUID tenantId, LocalDateTime since, Pageable pageable);

    Page<AuditLog> findByTenantIdAndActionAndCreatedAtAfterOrderByCreatedAtDesc(
        UUID tenantId, String action, LocalDateTime since, Pageable pageable);
}
