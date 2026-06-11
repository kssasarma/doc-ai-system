package com.docai.bot.adapter.rest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.DocumentationGapService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.DocumentationGapReport;
import com.docai.bot.domain.repository.DocumentationGapReportRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/gap-reports")
@RequiredArgsConstructor
public class GapReportController {

    private final DocumentationGapReportRepository reportRepository;
    private final DocumentationGapService gapService;

    @GetMapping
    public ResponseEntity<List<DocumentationGapReport>> list(
            @RequestParam(required = false) String product,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!principal.isAdmin()) return ResponseEntity.status(403).build();

        List<DocumentationGapReport> reports = product != null
            ? reportRepository.findByProductOrderByReportPeriodEndDesc(product)
            : reportRepository.findAllByOrderByReportPeriodEndDesc();

        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentationGapReport> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!principal.isAdmin()) return ResponseEntity.status(403).build();
        return reportRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Trigger on-demand gap report generation. */
    @PostMapping("/generate")
    public ResponseEntity<DocumentationGapReport> generate(
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String version,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!principal.isAdmin()) return ResponseEntity.status(403).build();

        DocumentationGapReport report = gapService.generateReport(product, version);
        if (report == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(report);
    }

    /** Export a gap report as Markdown for technical writers. */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!principal.isAdmin()) return ResponseEntity.status(403).build();

        return reportRepository.findById(id)
            .map(report -> {
                // Mark exported
                report.setExportedAt(LocalDateTime.now());
                reportRepository.save(report);

                String markdown = buildMarkdown(report);
                byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
                String filename = "gap-report-" + report.getId().toString().substring(0, 8) + ".md";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
                return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(bytes);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private String buildMarkdown(DocumentationGapReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Documentation Gap Report\n\n");
        if (report.getProduct() != null) {
            md.append("**Product:** ").append(report.getProduct());
            if (report.getVersion() != null) md.append(" ").append(report.getVersion());
            md.append("  \n");
        }
        md.append("**Period:** ").append(report.getReportPeriodStart())
          .append(" → ").append(report.getReportPeriodEnd()).append("  \n");
        md.append("**Total low-confidence queries:** ").append(report.getTotalLowConfidenceQueries()).append("  \n");
        md.append("**Generated:** ").append(report.getGeneratedAt()).append("\n\n---\n\n");
        md.append("## Documentation Gaps\n\n");
        md.append("The following topics had no or insufficient documentation coverage:\n\n");
        md.append(report.getGapTopics());
        return md.toString();
    }
}
