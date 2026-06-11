package com.docai.bot.application.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.DigestProperties;
import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.EmailDigest;
import com.docai.bot.domain.entity.EmailDigest.Frequency;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.EmailDigestRepository;
import com.docai.bot.domain.repository.QueryLogRepository;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDigestService {

    private final EmailDigestRepository digestRepository;
    private final QueryLogRepository queryLogRepository;
    private final DocumentRepository documentRepository;
    private final JavaMailSender mailSender;
    private final DigestProperties props;
    private final UserRepository userRepository;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<EmailDigest> findByUser(UUID userId) {
        return digestRepository.findByUserId(userId);
    }

    @Transactional
    public EmailDigest upsert(UUID userId, Frequency frequency, Short sendDay, short sendHour,
                              String productFilter, String versionFilter, boolean enabled) {
        EmailDigest digest = digestRepository.findByUserId(userId).orElseGet(() ->
            EmailDigest.builder().userId(userId).build());

        digest.setFrequency(frequency != null ? frequency : Frequency.WEEKLY);
        digest.setSendDay(sendDay);
        digest.setSendHour(sendHour);
        digest.setProductFilter(productFilter);
        digest.setVersionFilter(versionFilter);
        digest.setEnabled(enabled);
        digest.setNextSendAt(computeNextSendAt(digest));

        return digestRepository.save(digest);
    }

    @Transactional
    public void disable(UUID userId) {
        digestRepository.findByUserId(userId).ifPresent(d -> {
            d.setEnabled(false);
            digestRepository.save(d);
        });
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 3_600_000) // run every hour
    public void processDueDigests() {
        List<EmailDigest> due = digestRepository.findByEnabledTrueAndNextSendAtBefore(Instant.now());
        if (due.isEmpty()) return;
        log.info("Processing {} due email digests", due.size());
        for (EmailDigest digest : due) {
            try {
                sendDigest(digest);
                digest.setLastSentAt(Instant.now());
                digest.setNextSendAt(computeNextSendAt(digest));
                digestRepository.save(digest);
            } catch (Exception e) {
                log.error("Failed to send digest {} for user {}: {}", digest.getId(), digest.getUserId(), e.getMessage());
            }
        }
    }

    // ── Email sending ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void sendDigest(EmailDigest digest) throws MessagingException, java.io.UnsupportedEncodingException {
        String email = userRepository.findById(digest.getUserId())
            .map(u -> u.getEmail())
            .orElse(null);
        if (email == null || email.isBlank()) {
            log.warn("Skipping digest for user {} — no email address", digest.getUserId());
            return;
        }

        LocalDateTime since = periodStart(digest.getFrequency());
        List<Object[]> topQueries = queryLogRepository.findTopQuestions(since, PageRequest.of(0, 5));
        List<Document> recentDocs = loadRecentDocuments(digest, since);

        String html = buildHtml(digest, topQueries, recentDocs);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(props.getFromAddress(), props.getFromName());
        helper.setTo(email);
        helper.setSubject(buildSubject(digest));
        helper.setText(html, true);
        mailSender.send(message);

        log.info("Sent {} digest to {} (user {})", digest.getFrequency(), email, digest.getUserId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime periodStart(Frequency frequency) {
        return switch (frequency) {
            case DAILY -> LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
            case WEEKLY -> LocalDateTime.now(ZoneOffset.UTC).minusWeeks(1);
            case MONTHLY -> LocalDateTime.now(ZoneOffset.UTC).minusMonths(1);
        };
    }

    private List<Document> loadRecentDocuments(EmailDigest digest, LocalDateTime since) {
        List<Document> all = documentRepository.findAll();
        return all.stream()
            .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isAfter(since))
            .filter(d -> digest.getProductFilter() == null || digest.getProductFilter().equalsIgnoreCase(d.getProduct()))
            .limit(5)
            .toList();
    }

    private String buildSubject(EmailDigest digest) {
        String period = switch (digest.getFrequency()) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
        };
        String product = digest.getProductFilter() != null ? " · " + digest.getProductFilter() : "";
        return period + " Docs-inator Digest" + product;
    }

    private String buildHtml(EmailDigest digest, List<Object[]> topQueries, List<Document> recentDocs) {
        String appUrl = props.getAppUrl();
        String period = switch (digest.getFrequency()) {
            case DAILY -> "past 24 hours";
            case WEEKLY -> "past 7 days";
            case MONTHLY -> "past 30 days";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8" />
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                       background: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
                .card { background: #ffffff; border-radius: 12px; padding: 24px 28px;
                        max-width: 560px; margin: 0 auto; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
                h1 { font-size: 20px; font-weight: 700; margin: 0 0 4px; color: #1d4ed8; }
                h2 { font-size: 14px; font-weight: 600; margin: 20px 0 8px; color: #475569; text-transform: uppercase; letter-spacing: 0.05em; }
                p  { font-size: 14px; margin: 0 0 16px; color: #64748b; }
                ul { margin: 0 0 16px; padding-left: 18px; }
                li { font-size: 14px; margin-bottom: 6px; color: #334155; }
                .badge { display: inline-block; padding: 2px 8px; border-radius: 9999px;
                         background: #dbeafe; color: #1d4ed8; font-size: 11px; font-weight: 600; margin-left: 4px; }
                .btn { display: inline-block; margin-top: 20px; padding: 10px 20px;
                       background: #1d4ed8; color: #ffffff; border-radius: 8px;
                       text-decoration: none; font-size: 14px; font-weight: 600; }
                .footer { text-align: center; font-size: 11px; color: #94a3b8; margin-top: 24px; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>📚 Docs-inator Digest</h1>
                <p>Here's your summary for the <strong>""").append(period).append("</strong>.");
        if (digest.getProductFilter() != null) {
            sb.append(" Product: <span class=\"badge\">").append(escape(digest.getProductFilter())).append("</span>");
        }
        sb.append("</p>");

        // Top Questions
        if (!topQueries.isEmpty()) {
            sb.append("<h2>Most Asked Questions</h2><ul>");
            for (Object[] row : topQueries) {
                String question = (String) row[0];
                long count = ((Number) row[1]).longValue();
                String prod = row[2] != null ? " (" + row[2] + ")" : "";
                sb.append("<li>").append(escape(question)).append(prod)
                  .append(" <span class=\"badge\">×").append(count).append("</span></li>");
            }
            sb.append("</ul>");
        }

        // Recently Ingested Documents
        if (!recentDocs.isEmpty()) {
            sb.append("<h2>Newly Ingested Documents</h2><ul>");
            for (Document doc : recentDocs) {
                sb.append("<li>").append(escape(doc.getDocumentName()))
                  .append(" <span class=\"badge\">").append(escape(doc.getProduct()))
                  .append(" ").append(escape(doc.getVersion())).append("</span></li>");
            }
            sb.append("</ul>");
        }

        if (topQueries.isEmpty() && recentDocs.isEmpty()) {
            sb.append("<p>No activity during this period. Ask your team to start querying the docs!</p>");
        }

        sb.append("<a href=\"").append(escape(appUrl)).append("\" class=\"btn\">Open Docs-inator ↗</a>")
          .append("<div class=\"footer\">You're receiving this because you enabled email digests.")
          .append(" <a href=\"").append(escape(appUrl)).append("/api-keys\">Manage settings</a>")
          .append("</div></div></body></html>");

        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    Instant computeNextSendAt(EmailDigest digest) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime base = now.withMinute(0).withSecond(0).withNano(0)
            .withHour(digest.getSendHour());

        return switch (digest.getFrequency()) {
            case DAILY -> {
                ZonedDateTime candidate = base.plusDays(1);
                yield candidate.toInstant();
            }
            case WEEKLY -> {
                int day = digest.getSendDay() != null ? digest.getSendDay() : 1; // 1=MON
                DayOfWeek dow = DayOfWeek.of(((day - 1) % 7) + 1);
                ZonedDateTime candidate = base.with(TemporalAdjusters.nextOrSame(dow));
                if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
                yield candidate.toInstant();
            }
            case MONTHLY -> {
                int dom = digest.getSendDay() != null ? Math.min(Math.max(digest.getSendDay(), 1), 28) : 1;
                ZonedDateTime candidate = base.withDayOfMonth(dom);
                if (!candidate.isAfter(now)) candidate = candidate.plusMonths(1).withDayOfMonth(dom);
                yield candidate.toInstant();
            }
        };
    }
}
