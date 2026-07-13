package com.docai.ingestor.adapter.rest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.ingestor.config.JwtTokenFilter.AdminPrincipal;
import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.PiiFlag;
import com.docai.ingestor.domain.repository.PiiFlagRepository;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/** Admin review surface for PiiDetectionService's findings — see IngestionService, which runs
 * the scan on every ingested document. */
@RestController
@RequestMapping("/api/pii-flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PiiFlagController {

    private final PiiFlagRepository piiFlagRepository;

    @GetMapping
    public ResponseEntity<List<PiiFlag>> list(@RequestParam(defaultValue = "false") boolean reviewed) {
        return ResponseEntity.ok(
            piiFlagRepository.findByTenantIdAndReviewed(TenantContext.get(), reviewed));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<PiiFlag> review(@PathVariable UUID id, @RequestBody ReviewRequest request,
                                           @AuthenticationPrincipal AdminPrincipal principal) {
        PiiFlag flag = piiFlagRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("PII flag not found"));
        if (!TenantContext.get().equals(flag.getTenantId())) {
            throw new AccessDeniedException("You do not have access to this PII flag");
        }
        flag.setReviewed(true);
        flag.setActionTaken(request.getActionTaken());
        flag.setReviewedBy(principal.userId());
        flag.setReviewedAt(LocalDateTime.now());
        return ResponseEntity.ok(piiFlagRepository.save(flag));
    }

    @Data
    static class ReviewRequest {
        @NotBlank(message = "actionTaken is required")
        private String actionTaken; // e.g. ACKNOWLEDGED, REDACTED, DISMISSED
    }
}
