package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.TenantMembership;

@Repository
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    List<TenantMembership> findByUserId(UUID userId);
    Optional<TenantMembership> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);
    void deleteByUserId(UUID userId);
    long countByTenantId(UUID tenantId);
}
