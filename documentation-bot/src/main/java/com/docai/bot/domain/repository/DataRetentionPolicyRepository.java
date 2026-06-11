package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.DataRetentionPolicy;

public interface DataRetentionPolicyRepository extends JpaRepository<DataRetentionPolicy, UUID> {

    Optional<DataRetentionPolicy> findByTenantId(UUID tenantId);
}
