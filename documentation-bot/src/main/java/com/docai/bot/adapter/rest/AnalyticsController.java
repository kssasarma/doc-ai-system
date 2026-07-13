package com.docai.bot.adapter.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AnalyticsService;
import com.docai.bot.config.UserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsService.OverviewDTO> getOverview(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getOverview(principal.tenantId()));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<AnalyticsService.DailyStatDTO>> getDailyStats(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getDailyStats(principal.tenantId(), days));
    }

    @GetMapping("/top-questions")
    public ResponseEntity<List<AnalyticsService.TopQuestionDTO>> getTopQuestions(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getTopQuestions(principal.tenantId(), limit));
    }

    @GetMapping("/product-coverage")
    public ResponseEntity<List<AnalyticsService.ProductCoverageDTO>> getProductCoverage(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getProductCoverage(principal.tenantId()));
    }

    @GetMapping("/user-engagement")
    public ResponseEntity<List<AnalyticsService.UserEngagementDTO>> getUserEngagement(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getUserEngagement(principal.tenantId()));
    }

    @GetMapping("/cost")
    public ResponseEntity<AnalyticsService.CostSummaryDTO> getCostSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getCostSummary(principal.tenantId()));
    }

    @GetMapping("/document-coverage")
    public ResponseEntity<List<AnalyticsService.DocumentCoverageDTO>> getDocumentCoverage(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getDocumentCoverage(principal.tenantId()));
    }

    @GetMapping("/failed-queries")
    public ResponseEntity<List<AnalyticsService.FailedQueryDTO>> getFailedQueries(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getFailedQueries(principal.tenantId(), limit));
    }
}
