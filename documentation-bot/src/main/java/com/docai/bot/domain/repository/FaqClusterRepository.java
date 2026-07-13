package com.docai.bot.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.FaqCluster;

@Repository
public interface FaqClusterRepository extends JpaRepository<FaqCluster, UUID> {

    List<FaqCluster> findByTenantIdAndProductAndVersionAndPeriodStartAndPeriodEnd(
        UUID tenantId, String product, String version, LocalDate periodStart, LocalDate periodEnd);

    List<FaqCluster> findByTenantIdAndProductAndPeriodStartGreaterThanEqualOrderByQueryCountDesc(
        UUID tenantId, String product, LocalDate since);
}
