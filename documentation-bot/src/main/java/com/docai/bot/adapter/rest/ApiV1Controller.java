package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.ChatRequest;
import com.docai.bot.application.service.ChatService.ChatResponse;
import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.VectorSearchService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.model.SearchScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Public REST API v1 — authenticated with API keys (X-API-Key header or Authorization: ApiKey …).
 * Enables programmatic access, Slack bots, CI/CD integrations, and browser/IDE extensions.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiV1Controller {

    private final ChatService chatService;
    private final VectorSearchService vectorSearchService;
    private final DocumentAccessPolicy documentAccessPolicy;

    // ── POST /api/v1/query ────────────────────────────────────────────────────

    @PostMapping("/query")
    public ResponseEntity<?> query(
            @Valid @RequestBody QueryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(new ErrorResponse(
                "UNAUTHORIZED", "A valid API key is required. Pass it as X-API-Key header or Authorization: ApiKey <key>"));
        }

        ChatRequest chatRequest = ChatRequest.builder()
            .chatId(request.getChatId())
            .product(request.getProduct())
            .version(request.getVersion())
            .question(request.getQuestion())
            .userId(principal.userId())
            .build();

        ChatResponse chatResponse = chatService.processQuery(chatRequest);

        return ResponseEntity.ok(ApiQueryResponse.builder()
            .chatId(chatResponse.getChatId())
            .messageId(chatResponse.getMessageId())
            .answer(chatResponse.getAnswer())
            .confidence(chatResponse.getConfidence())
            .confidenceLabel(confidenceLabel(chatResponse.getConfidence()))
            .sources(chatResponse.getSources() != null
                ? chatResponse.getSources().stream()
                    .map(s -> ApiSource.builder()
                        .document(s.getDocument())
                        .product(s.getProduct())
                        .version(s.getVersion())
                        .relevanceScore(s.getRelevanceScore())
                        .excerpt(s.getExcerpt())
                        .build())
                    .collect(Collectors.toList())
                : List.of())
            .relatedQuestions(chatResponse.getRelatedQuestions())
            .build());
    }

    // ── GET /api/v1/search ────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam @NotBlank String q,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "7") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(new ErrorResponse(
                "UNAUTHORIZED", "A valid API key is required."));
        }

        // product/version are accepted for API back-compat but no longer filter eligibility —
        // access grants are the sole gate (see DocumentAccessPolicy).
        SearchScope scope = documentAccessPolicy.resolveScope(principal.userId(), TenantContext.get());
        var chunks = vectorSearchService.search(q, scope);
        var results = chunks.stream()
            .limit(limit)
            .map(c -> SearchResult.builder()
                .documentName(c.getDocumentName())
                .product(c.getProduct())
                .version(c.getVersion())
                .relevanceScore(c.getSimilarity())
                .excerpt(c.getContent().length() > 300 ? c.getContent().substring(0, 300) + "…" : c.getContent())
                .build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    // ── GET /api/v1/health ────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ok", "docs-ai-system API v1"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String confidenceLabel(double confidence) {
        if (confidence >= 0.80) return "HIGH";
        if (confidence >= 0.60) return "MEDIUM";
        return "LOW";
    }

    // ── Request/Response DTOs ─────────────────────────────────────────────────

    @Data
    static class QueryRequest {
        private String chatId;
        private String product;
        private String version;
        @NotBlank(message = "question is required")
        @Size(max = 2000, message = "question must not exceed 2000 characters")
        private String question;
    }

    @lombok.Data @lombok.Builder
    static class ApiQueryResponse {
        private String chatId;
        private String messageId;
        private String answer;
        private double confidence;
        private String confidenceLabel;
        private List<ApiSource> sources;
        private List<String> relatedQuestions;
    }

    @lombok.Data @lombok.Builder
    static class ApiSource {
        private String document;
        private String product;
        private String version;
        private double relevanceScore;
        private String excerpt;
    }

    @lombok.Data @lombok.Builder
    static class SearchResult {
        private String documentName;
        private String product;
        private String version;
        private double relevanceScore;
        private String excerpt;
    }

    record HealthResponse(String status, String service) {}

    record ErrorResponse(String code, String message) {}
}
