package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.TenantLLMConfig;

public interface TenantLLMConfigRepository extends JpaRepository<TenantLLMConfig, UUID> {

    Optional<TenantLLMConfig> findByTenantId(UUID tenantId);
}
