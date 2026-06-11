package com.docai.bot.adapter.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AnalyticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsService.OverviewDTO> getOverview() {
        return ResponseEntity.ok(analyticsService.getOverview());
    }

    @GetMapping("/daily")
    public ResponseEntity<List<AnalyticsService.DailyStatDTO>> getDailyStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getDailyStats(days));
    }

    @GetMapping("/top-questions")
    public ResponseEntity<List<AnalyticsService.TopQuestionDTO>> getTopQuestions(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopQuestions(limit));
    }

    @GetMapping("/product-coverage")
    public ResponseEntity<List<AnalyticsService.ProductCoverageDTO>> getProductCoverage() {
        return ResponseEntity.ok(analyticsService.getProductCoverage());
    }

    @GetMapping("/user-engagement")
    public ResponseEntity<List<AnalyticsService.UserEngagementDTO>> getUserEngagement() {
        return ResponseEntity.ok(analyticsService.getUserEngagement());
    }

    @GetMapping("/cost")
    public ResponseEntity<AnalyticsService.CostSummaryDTO> getCostSummary() {
        return ResponseEntity.ok(analyticsService.getCostSummary());
    }

    @GetMapping("/document-coverage")
    public ResponseEntity<List<AnalyticsService.DocumentCoverageDTO>> getDocumentCoverage() {
        return ResponseEntity.ok(analyticsService.getDocumentCoverage());
    }

    @GetMapping("/failed-queries")
    public ResponseEntity<List<AnalyticsService.FailedQueryDTO>> getFailedQueries(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(analyticsService.getFailedQueries(limit));
    }
}
