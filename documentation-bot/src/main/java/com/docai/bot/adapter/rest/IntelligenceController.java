package com.docai.bot.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AnswerEvolutionService;
import com.docai.bot.application.service.VersionDiffService;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    private final VersionDiffService versionDiffService;
    private final AnswerEvolutionService answerEvolutionService;

    /**
     * POST /api/intelligence/version-diff
     * Compare documentation for a topic between two versions.
     */
    @PostMapping("/version-diff")
    public ResponseEntity<VersionDiffService.DiffResult> versionDiff(
            @Valid @RequestBody VersionDiffRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        VersionDiffService.DiffResult result = versionDiffService.diff(
            request.getTopic(), request.getProduct(),
            request.getVersionA(), request.getVersionB()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/intelligence/evolution?question=...&product=...
     * Show how the answer to a question changed across all versions of a product.
     */
    @GetMapping("/evolution")
    public ResponseEntity<AnswerEvolutionService.EvolutionTimeline> evolution(
            @RequestParam @NotBlank String question,
            @RequestParam @NotBlank String product,
            @AuthenticationPrincipal UserPrincipal principal) {

        AnswerEvolutionService.EvolutionTimeline timeline =
            answerEvolutionService.getEvolution(question, product);
        return ResponseEntity.ok(timeline);
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    @Data
    static class VersionDiffRequest {
        @NotBlank private String topic;
        @NotBlank private String product;
        @NotBlank private String versionA;
        @NotBlank private String versionB;
    }
}
