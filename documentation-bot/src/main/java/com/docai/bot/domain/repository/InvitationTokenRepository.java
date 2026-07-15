package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.InvitationToken;

@Repository
public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);

    long countByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(UUID tenantId, LocalDateTime now);

    List<InvitationToken> findByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
        UUID tenantId, LocalDateTime now);
}
