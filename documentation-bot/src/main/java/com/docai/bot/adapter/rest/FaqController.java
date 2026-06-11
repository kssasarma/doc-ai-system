package com.docai.bot.adapter.rest;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AutoFaqService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.FaqEntry;
import com.docai.bot.domain.entity.FaqEntry.Status;
import com.docai.bot.domain.repository.FaqEntryRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqEntryRepository faqEntryRepository;
    private final AutoFaqService autoFaqService;

    /** Public: browse approved FAQ entries. */
    @GetMapping
    public ResponseEntity<Page<FaqEntry>> listApproved(
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("helpfulCount").descending());
        Page<FaqEntry> result;
        if (product != null && version != null) {
            result = faqEntryRepository.findByProductAndVersionAndStatus(product, version, Status.APPROVED, pageable);
        } else if (product != null) {
            result = faqEntryRepository.findByProductAndStatus(product, Status.APPROVED, pageable);
        } else {
            result = faqEntryRepository.findByStatus(Status.APPROVED, pageable);
        }
        return ResponseEntity.ok(result);
    }

    /** Public: view a single FAQ entry (increments view count). */
    @GetMapping("/{id}")
    public ResponseEntity<FaqEntry> getEntry(@PathVariable UUID id) {
        return faqEntryRepository.findById(id)
            .filter(e -> e.getStatus() == Status.APPROVED)
            .map(e -> {
                faqEntryRepository.incrementViewCount(id);
                return ResponseEntity.ok(e);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Authenticated: mark a FAQ entry as helpful. */
    @PostMapping("/{id}/helpful")
    public ResponseEntity<Void> markHelpful(@PathVariable UUID id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        faqEntryRepository.findById(id)
            .filter(e -> e.getStatus() == Status.APPROVED)
            .ifPresent(e -> faqEntryRepository.incrementHelpfulCount(id));
        return ResponseEntity.noContent().build();
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────

    /** Admin: list all PENDING entries for review. */
    @GetMapping("/admin/pending")
    public ResponseEntity<Page<FaqEntry>> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!principal.isAdmin()) return ResponseEntity.status(403).build();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(faqEntryRepository.findByStatus(Status.PENDING, pageable));
    }

    /** Admin: approve a pending FAQ entry. */
    @PutMapping("/admin/{id}/approve")
    public ResponseEntity<FaqEntry> approve(@PathVariable UUID id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        if (!principal.isAdmin()) return ResponseEntity.status(403).build();
        return faqEntryRepository.findById(id)
            .map(entry -> {
                entry.setStatus(Status.APPROVED);
                entry.setApprovedBy(principal.userId());
                entry.setApprovedAt(LocalDateTime.now());
                return ResponseEntity.ok(faqEntryRepository.save(entry));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Admin: reject a pending FAQ entry. */
    @PutMapping("/admin/{id}/reject")
    public ResponseEntity<FaqEntry> reject(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        if (!principal.isAdmin()) return ResponseEntity.status(403).build();
        return faqEntryRepository.findById(id)
            .map(entry -> {
                entry.setStatus(Status.REJECTED);
                return ResponseEntity.ok(faqEntryRepository.save(entry));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Admin: trigger FAQ generation on-demand for a product. */
    @PostMapping("/admin/generate")
    public ResponseEntity<GenerateResponse> triggerGeneration(
            @RequestParam String product,
            @RequestParam(required = false) String version,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!principal.isAdmin()) return ResponseEntity.status(403).build();
        int count = autoFaqService.generateForProduct(product, version);
        return ResponseEntity.ok(new GenerateResponse(count, "FAQ generation complete"));
    }

    record GenerateResponse(int entriesGenerated, String message) {}
}
