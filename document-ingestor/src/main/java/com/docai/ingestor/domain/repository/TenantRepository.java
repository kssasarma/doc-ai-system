package com.docai.ingestor.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.ingestor.domain.entity.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
