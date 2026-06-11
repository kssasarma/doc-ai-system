package com.docai.ingestor.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.ingestor.domain.entity.PiiFlag;
import com.docai.ingestor.domain.repository.PiiFlagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scans document text for personally identifiable information (PII) before
 * or after ingestion.  Flags are stored in pii_flags and surfaced in the admin panel.
 *
 * Patterns are intentionally broad to minimise false negatives at the cost of some
 * false positives — admins can review and approve/dismiss each flag.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiDetectionService {

    private final PiiFlagRepository piiFlagRepository;

    private record PiiPattern(String type, Pattern pattern, String riskLevel) {}

    private static final List<PiiPattern> PATTERNS = List.of(
        new PiiPattern("EMAIL",
            Pattern.compile("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b"),
            "LOW"),
        new PiiPattern("PHONE",
            Pattern.compile("(?:\\+?\\d{1,3}[\\s\\-.])?\\(?\\d{3}\\)?[\\s\\-.]\\d{3}[\\s\\-.]\\d{4}\\b"),
            "MEDIUM"),
        new PiiPattern("SSN",
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            "CRITICAL"),
        new PiiPattern("CREDIT_CARD",
            Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b"),
            "CRITICAL"),
        new PiiPattern("IP_ADDRESS",
            Pattern.compile("\\b(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)" +
                            "(?:\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)){3}\\b"),
            "LOW"),
        new PiiPattern("AWS_KEY",
            Pattern.compile("\\b(?:AKIA|ASIA|AROA)[A-Z0-9]{16}\\b"),
            "CRITICAL")
    );

    /**
     * Scans {@code content} for PII and persists any findings as PiiFlag rows.
     *
     * @return true if any HIGH/CRITICAL PII was found (admin should review before publishing)
     */
    @Transactional
    public boolean scanAndFlag(UUID documentId, UUID tenantId, String content) {
        List<PiiFlag> flags = new ArrayList<>();
        for (PiiPattern pp : PATTERNS) {
            Matcher m = pp.pattern().matcher(content);
            int count = 0;
            String sample = null;
            while (m.find()) {
                count++;
                if (sample == null) {
                    // store a redacted excerpt (first 40 chars of surrounding context)
                    int start = Math.max(0, m.start() - 10);
                    int end   = Math.min(content.length(), m.end() + 10);
                    sample = redact(content.substring(start, end), pp.type());
                }
            }
            if (count > 0) {
                flags.add(PiiFlag.builder()
                    .documentId(documentId)
                    .tenantId(tenantId)
                    .piiType(pp.type())
                    .occurrenceCount(count)
                    .sampleExcerpt(sample)
                    .riskLevel(pp.riskLevel())
                    .build());
                log.warn("PII detected type={} count={} doc={}", pp.type(), count, documentId);
            }
        }
        if (!flags.isEmpty()) {
            piiFlagRepository.saveAll(flags);
        }
        return flags.stream().anyMatch(f -> "HIGH".equals(f.getRiskLevel()) || "CRITICAL".equals(f.getRiskLevel()));
    }

    private String redact(String excerpt, String type) {
        return "…[" + type + " redacted]…";
    }
}
