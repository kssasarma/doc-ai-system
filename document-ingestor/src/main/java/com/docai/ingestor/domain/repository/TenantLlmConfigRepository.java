package com.docai.ingestor.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.ingestor.domain.entity.TenantLlmConfig;

@Repository
public interface TenantLlmConfigRepository extends JpaRepository<TenantLlmConfig, UUID> {

    Optional<TenantLlmConfig> findByTenantId(UUID tenantId);
}
