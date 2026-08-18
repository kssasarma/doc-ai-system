package com.docai.bot.application.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.FaqEntry;
import com.docai.bot.domain.entity.FaqEntry.Status;
import com.docai.bot.domain.repository.FaqEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Closes the feedback loop between observed user helpfulness and FAQ quality.
 *
 * An approved FAQ entry that accumulates enough views but a low helpfulness rate has likely
 * become stale (the answer is outdated, ambiguous, or off-topic for current users). This service
 * resets such entries to PENDING so an admin sees them alongside the next batch of generated
 * entries and can update, re-approve, or reject them.
 *
 * The stale check runs weekly, after FAQ generation, so the pending queue always shows
 * newly generated entries and re-surfaced stale ones together — a single admin review pass
 * handles both.
 *
 * No TenantContext is required here: no LLM calls are made, only DB reads and writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqMaintenanceService {

    /**
     * Minimum views before a helpfulness rate is considered statistically meaningful. Below this
     * threshold, low helpful counts are noise (one person visited and didn't click). At 20 views
     * the signal is reliable enough to flag for review without over-triggering on low-traffic FAQs.
     */
    private static final int MIN_VIEWS_FOR_FEEDBACK = 20;

    /**
     * Approved entries where fewer than 20% of viewers clicked "Helpful" are considered stale.
     * Industry benchmarks for self-service documentation suggest 20–30% as a healthy floor; 20%
     * is deliberately conservative to avoid flagging FAQs that are merely niche, not wrong.
     */
    private static final double MIN_HELPFULNESS_RATE = 0.20;

    private final FaqEntryRepository faqEntryRepository;

    /**
     * Runs every Sunday at 04:00 UTC — two hours after FAQ generation (02:00) so newly generated
     * PENDING entries don't interfere with the stale scan, and admins see a unified pending queue.
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    public void flagStaleEntries() {
        log.info("FaqMaintenanceService: scanning for low-helpfulness approved FAQ entries");

        List<FaqEntry> candidates = faqEntryRepository.findApprovedWithMinViews(MIN_VIEWS_FOR_FEEDBACK);
        int flagged = 0;

        for (FaqEntry entry : candidates) {
            double rate = (double) entry.getHelpfulCount() / entry.getViewCount();
            if (rate < MIN_HELPFULNESS_RATE) {
                entry.setStatus(Status.PENDING);
                entry.setReviewNote(String.format(
                    "Auto-flagged: only %d of %d viewer(s) found this helpful (%.0f%%). "
                    + "The answer may be outdated — please review and update or re-approve.",
                    entry.getHelpfulCount(), entry.getViewCount(), rate * 100));
                faqEntryRepository.save(entry);
                flagged++;
                log.info("FaqMaintenanceService: flagged stale FAQ '{}' ({}/{} helpful, {:.0f}%)",
                    entry.getQuestion(), entry.getHelpfulCount(), entry.getViewCount(), rate * 100);
            }
        }

        log.info("FaqMaintenanceService: flagged {} stale entries for re-review", flagged);
    }
}
