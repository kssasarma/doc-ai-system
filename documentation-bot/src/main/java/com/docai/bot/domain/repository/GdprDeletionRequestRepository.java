package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.GdprDeletionRequest;

public interface GdprDeletionRequestRepository extends JpaRepository<GdprDeletionRequest, UUID> {

    List<GdprDeletionRequest> findByTenantIdAndStatus(UUID tenantId, String status);

    List<GdprDeletionRequest> findByUserId(UUID userId);

    List<GdprDeletionRequest> findByStatusAndRequestedAtBefore(String status, LocalDateTime cutoff);
}
