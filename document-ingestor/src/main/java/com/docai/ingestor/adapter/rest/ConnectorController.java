package com.docai.ingestor.adapter.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.ingestor.application.service.ConfluenceConnectorService;
import com.docai.ingestor.application.service.NotionConnectorService;
import com.docai.ingestor.domain.entity.IntegrationToken;
import com.docai.ingestor.domain.repository.IntegrationTokenRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final IntegrationTokenRepository tokenRepository;
    private final ConfluenceConnectorService confluenceService;
    private final NotionConnectorService notionService;

    // ── Confluence ────────────────────────────────────────────────────────────

    /** Register / update a Confluence OAuth token. */
    @PostMapping("/confluence/token")
    public ResponseEntity<TokenResponse> saveConfluenceToken(
            @Valid @RequestBody ConfluenceTokenRequest req,
            Authentication auth) {

        UUID userId = resolveUserId(auth);
        Instant expiresAt = req.getExpiresInSeconds() != null
            ? Instant.now().plusSeconds(req.getExpiresInSeconds())
            : null;

        IntegrationToken token = confluenceService.saveToken(
            userId, req.getAccessToken(), req.getRefreshToken(), expiresAt, req.getSiteUrl());

        log.info("Confluence token saved for user {}", userId);
        return ResponseEntity.ok(toDto(token));
    }

    /** Trigger an async sync of a Confluence space. */
    @PostMapping("/confluence/sync")
    public ResponseEntity<Map<String, String>> syncConfluenceSpace(
            @Valid @RequestBody ConfluenceSyncRequest req,
            Authentication auth) {

        UUID userId = resolveUserId(auth);
        IntegrationToken token = tokenRepository
            .findByUserIdAndProvider(userId, IntegrationToken.Provider.confluence)
            .orElseThrow(() -> new IllegalStateException("No Confluence token found. Register one first."));

        confluenceService.syncSpace(token.getId(), req.getSpaceKey(), req.getProduct(), req.getVersion());

        return ResponseEntity.accepted().body(Map.of(
            "message", "Confluence sync started for space " + req.getSpaceKey(),
            "tokenId", token.getId().toString()
        ));
    }

    @GetMapping("/confluence/stats")
    public ResponseEntity<Map<String, Object>> confluenceStats(Authentication auth) {
        UUID userId = resolveUserId(auth);
        return tokenRepository.findByUserIdAndProvider(userId, IntegrationToken.Provider.confluence)
            .map(token -> ResponseEntity.ok(confluenceService.getSyncStats(token.getId())))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Notion ────────────────────────────────────────────────────────────────

    @PostMapping("/notion/token")
    public ResponseEntity<TokenResponse> saveNotionToken(
            @Valid @RequestBody NotionTokenRequest req,
            Authentication auth) {

        UUID userId = resolveUserId(auth);
        IntegrationToken token = notionService.saveToken(
            userId, req.getAccessToken(), req.getWorkspaceId(), req.getWorkspaceName());

        log.info("Notion token saved for user {}", userId);
        return ResponseEntity.ok(toDto(token));
    }

    @PostMapping("/notion/sync")
    public ResponseEntity<Map<String, String>> syncNotion(
            @Valid @RequestBody NotionSyncRequest req,
            Authentication auth) {

        UUID userId = resolveUserId(auth);
        IntegrationToken token = tokenRepository
            .findByUserIdAndProvider(userId, IntegrationToken.Provider.notion)
            .orElseThrow(() -> new IllegalStateException("No Notion token found. Register one first."));

        notionService.syncAllPages(token.getId(), req.getProduct(), req.getVersion());

        return ResponseEntity.accepted().body(Map.of(
            "message", "Notion sync started",
            "tokenId", token.getId().toString()
        ));
    }

    @GetMapping("/notion/stats")
    public ResponseEntity<Map<String, Object>> notionStats(Authentication auth) {
        UUID userId = resolveUserId(auth);
        return tokenRepository.findByUserIdAndProvider(userId, IntegrationToken.Provider.notion)
            .map(token -> ResponseEntity.ok(notionService.getSyncStats(token.getId())))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<TokenResponse>> listTokens(Authentication auth) {
        UUID userId = resolveUserId(auth);
        List<TokenResponse> tokens = tokenRepository.findByUserId(userId)
            .stream().map(this::toDto).toList();
        return ResponseEntity.ok(tokens);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable UUID id, Authentication auth) {
        UUID userId = resolveUserId(auth);
        tokenRepository.findById(id).ifPresent(token -> {
            if (!token.getUserId().equals(userId)) throw new IllegalArgumentException("Not your token");
            tokenRepository.delete(token);
        });
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof com.docai.ingestor.config.JwtTokenFilter.AdminPrincipal ap) {
            return ap.userId();
        }
        throw new IllegalStateException("Cannot resolve user id from principal: " + principal.getClass().getSimpleName());
    }

    private TokenResponse toDto(IntegrationToken t) {
        return TokenResponse.builder()
            .id(t.getId().toString())
            .provider(t.getProvider().name())
            .siteUrl(t.getSiteUrl())
            .workspaceName(t.getWorkspaceName())
            .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
            .build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Data static class ConfluenceTokenRequest {
        @NotBlank private String accessToken;
        private String refreshToken;
        private Long expiresInSeconds;
        @NotBlank private String siteUrl;
    }

    @Data static class ConfluenceSyncRequest {
        @NotBlank private String spaceKey;
        @NotBlank private String product;
        @NotBlank private String version;
    }

    @Data static class NotionTokenRequest {
        @NotBlank private String accessToken;
        @NotBlank private String workspaceId;
        private String workspaceName;
    }

    @Data static class NotionSyncRequest {
        @NotBlank private String product;
        @NotBlank private String version;
    }

    @Data @Builder static class TokenResponse {
        private String id;
        private String provider;
        private String siteUrl;
        private String workspaceName;
        private String createdAt;
    }
}
