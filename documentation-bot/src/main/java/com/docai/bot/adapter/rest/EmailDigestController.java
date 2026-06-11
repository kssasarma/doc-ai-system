package com.docai.bot.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.EmailDigestService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.EmailDigest;
import com.docai.bot.domain.entity.EmailDigest.Frequency;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/digest")
@RequiredArgsConstructor
public class EmailDigestController {

    private final EmailDigestService digestService;

    /** GET /api/digest — fetch current user's digest preferences. */
    @GetMapping
    public ResponseEntity<DigestDTO> get(@AuthenticationPrincipal UserPrincipal principal) {
        return digestService.findByUser(principal.userId())
            .map(d -> ResponseEntity.ok(toDto(d)))
            .orElse(ResponseEntity.ok(defaultDto()));
    }

    /** PUT /api/digest — create or update digest preferences. */
    @PutMapping
    public ResponseEntity<DigestDTO> upsert(
            @Valid @RequestBody DigestRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        EmailDigest saved = digestService.upsert(
            principal.userId(),
            request.getFrequency() != null ? request.getFrequency() : Frequency.WEEKLY,
            request.getSendDay(),
            request.getSendHour() != null ? request.getSendHour().shortValue() : 8,
            request.getProductFilter(),
            request.getVersionFilter(),
            request.isEnabled()
        );
        return ResponseEntity.ok(toDto(saved));
    }

    /** DELETE /api/digest — disable digest without deleting preferences. */
    @DeleteMapping
    public ResponseEntity<Void> disable(@AuthenticationPrincipal UserPrincipal principal) {
        digestService.disable(principal.userId());
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Data
    static class DigestRequest {
        private Frequency frequency;
        private Short sendDay;
        @Min(0) @Max(23)
        private Integer sendHour;
        private String productFilter;
        private String versionFilter;
        private boolean enabled = true;
    }

    @lombok.Data @lombok.Builder
    static class DigestDTO {
        private String id;
        private boolean enabled;
        private String frequency;
        private Short sendDay;
        private short sendHour;
        private String productFilter;
        private String versionFilter;
        private String lastSentAt;
        private String nextSendAt;
    }

    private DigestDTO toDto(EmailDigest d) {
        return DigestDTO.builder()
            .id(d.getId() != null ? d.getId().toString() : null)
            .enabled(d.isEnabled())
            .frequency(d.getFrequency().name())
            .sendDay(d.getSendDay())
            .sendHour(d.getSendHour())
            .productFilter(d.getProductFilter())
            .versionFilter(d.getVersionFilter())
            .lastSentAt(d.getLastSentAt() != null ? d.getLastSentAt().toString() : null)
            .nextSendAt(d.getNextSendAt() != null ? d.getNextSendAt().toString() : null)
            .build();
    }

    private DigestDTO defaultDto() {
        return DigestDTO.builder()
            .enabled(false)
            .frequency(Frequency.WEEKLY.name())
            .sendHour((short) 8)
            .build();
    }
}
