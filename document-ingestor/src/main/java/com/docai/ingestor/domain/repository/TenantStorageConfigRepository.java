package com.docai.ingestor.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.ingestor.domain.entity.TenantStorageConfig;

public interface TenantStorageConfigRepository extends JpaRepository<TenantStorageConfig, UUID> {

    Optional<TenantStorageConfig> findByTenantId(UUID tenantId);
}
