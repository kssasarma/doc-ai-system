package com.docai.ingestor.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.ingestor.domain.entity.DocumentChunk;
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
     * @return true if any CRITICAL PII was found (the pattern set below never produces "HIGH" —
     *         its risk levels are LOW/MEDIUM/CRITICAL only; IngestionService quarantines the
     *         document rather than letting it go straight to COMPLETED when this is true)
     */
    @Transactional
    public boolean scanAndFlag(UUID documentId, UUID tenantId, String content) {
        return scanAndFlag(documentId, null, tenantId, content);
    }

    /**
     * Scans every chunk's own content and persists a chunk-scoped PiiFlag per finding — the
     * granularity that lets an admin exclude just the offending chunk (see PiiFlagController)
     * instead of the whole document. Independent of (and in addition to) the whole-document
     * scan {@link #scanAndFlag(UUID, UUID, String)} runs pre-chunking to decide quarantine;
     * best-effort like that scan, so a failure here never fails ingestion.
     *
     * @return true if any chunk carried CRITICAL PII
     */
    @Transactional
    public boolean scanChunksAndFlag(UUID documentId, UUID tenantId, List<DocumentChunk> chunks) {
        boolean anyCritical = false;
        for (DocumentChunk chunk : chunks) {
            if (scanAndFlag(documentId, chunk.getId(), tenantId, chunk.getContent())) {
                anyCritical = true;
            }
        }
        return anyCritical;
    }

    private boolean scanAndFlag(UUID documentId, UUID chunkId, UUID tenantId, String content) {
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
                    .documentChunkId(chunkId)
                    .tenantId(tenantId)
                    .piiType(pp.type())
                    .occurrenceCount(count)
                    .sampleExcerpt(sample)
                    .riskLevel(pp.riskLevel())
                    .build());
                log.warn("PII detected type={} count={} doc={} chunk={}", pp.type(), count, documentId, chunkId);
            }
        }
        if (!flags.isEmpty()) {
            piiFlagRepository.saveAll(flags);
        }
        return flags.stream().anyMatch(f -> "CRITICAL".equals(f.getRiskLevel()));
    }

    private String redact(String excerpt, String type) {
        return "…[" + type + " redacted]…";
    }
}
