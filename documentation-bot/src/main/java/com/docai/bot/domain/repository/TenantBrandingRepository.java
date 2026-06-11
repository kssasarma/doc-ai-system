package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.TenantBranding;

public interface TenantBrandingRepository extends JpaRepository<TenantBranding, UUID> {

    Optional<TenantBranding> findByTenantId(UUID tenantId);
}
