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
import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.VersionDiffService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.model.SearchScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Version-diff and evolution-timeline endpoints. Both resolve the caller's own {@link SearchScope}
 * before searching — until this fix, neither endpoint was tenant- or access-scoped at all (they
 * called {@link com.docai.bot.application.service.VectorSearchService}'s deprecated
 * {@code search(query, product, version)} overload directly), so any authenticated user, from any
 * tenant, could compare or browse any other tenant's documentation by product/version name alone.
 */
@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    private final VersionDiffService versionDiffService;
    private final AnswerEvolutionService answerEvolutionService;
    private final DocumentAccessPolicy documentAccessPolicy;

    /**
     * POST /api/intelligence/version-diff
     * Compare documentation for a topic between two versions, within the caller's own access.
     */
    @PostMapping("/version-diff")
    public ResponseEntity<VersionDiffService.DiffResult> versionDiff(
            @Valid @RequestBody VersionDiffRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        SearchScope scope = documentAccessPolicy.resolveScope(principal.userId(), TenantContext.get());
        VersionDiffService.DiffResult result = versionDiffService.diff(
            request.getTopic(), scope, request.getProduct(),
            request.getVersionA(), request.getVersionB()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/intelligence/evolution?question=...&product=...
     * Show how the answer to a question changed across all versions of a product the caller can access.
     */
    @GetMapping("/evolution")
    public ResponseEntity<AnswerEvolutionService.EvolutionTimeline> evolution(
            @RequestParam @NotBlank String question,
            @RequestParam @NotBlank String product,
            @AuthenticationPrincipal UserPrincipal principal) {

        SearchScope scope = documentAccessPolicy.resolveScope(principal.userId(), TenantContext.get());
        AnswerEvolutionService.EvolutionTimeline timeline =
            answerEvolutionService.getEvolution(question, scope, product);
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
