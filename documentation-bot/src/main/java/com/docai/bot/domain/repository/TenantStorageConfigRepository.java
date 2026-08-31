package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.TenantStorageConfig;

public interface TenantStorageConfigRepository extends JpaRepository<TenantStorageConfig, UUID> {

    Optional<TenantStorageConfig> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);
}
