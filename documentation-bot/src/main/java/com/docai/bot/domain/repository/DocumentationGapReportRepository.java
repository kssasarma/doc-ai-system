package com.docai.bot.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.DocumentationGapReport;

@Repository
public interface DocumentationGapReportRepository extends JpaRepository<DocumentationGapReport, UUID> {

    List<DocumentationGapReport> findByProductOrderByReportPeriodEndDesc(String product);

    List<DocumentationGapReport> findAllByOrderByReportPeriodEndDesc();

    Optional<DocumentationGapReport> findTopByProductAndVersionOrderByReportPeriodEndDesc(
        String product, String version);

    boolean existsByProductAndVersionAndReportPeriodEnd(
        String product, String version, LocalDate reportPeriodEnd);
}
