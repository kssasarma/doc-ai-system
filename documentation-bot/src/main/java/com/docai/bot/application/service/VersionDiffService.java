package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.2 — Version Diff Intelligence.
 * Retrieves documentation for the same topic from two versions and
 * generates a structured changelog: Added / Modified / Removed / Breaking changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionDiffService {

    private final VectorSearchService vectorSearchService;
    private final ChatClient.Builder chatClientBuilder;

    public record DiffResult(
        String topic,
        String product,
        String versionA,
        String versionB,
        String added,
        String modified,
        String removed,
        String breakingChanges,
        String summary
    ) {}

    /** {@code scope} is the caller's own resolved access — both per-version searches stay within
     * it, so the diff can never surface content from a version's documents the caller can't access. */
    public DiffResult diff(String topic, SearchScope scope, String product, String versionA, String versionB) {
        log.info("Computing version diff: {} — {} vs {}", topic, versionA, versionB);

        List<RetrievedChunk> chunksA = vectorSearchService.search(topic, scope.withVersionNarrow(product, versionA));
        List<RetrievedChunk> chunksB = vectorSearchService.search(topic, scope.withVersionNarrow(product, versionB));

        if (chunksA.isEmpty() && chunksB.isEmpty()) {
            return new DiffResult(topic, product, versionA, versionB,
                null, null, null, null,
                "No documentation found for this topic in either version.");
        }

        String prompt = buildDiffPrompt(topic, product, versionA, versionB, chunksA, chunksB);
        String raw = callLlm(prompt);
        return parseDiffResponse(topic, product, versionA, versionB, raw);
    }

    private String buildDiffPrompt(String topic, String product,
                                    String versionA, String versionB,
                                    List<RetrievedChunk> chunksA, List<RetrievedChunk> chunksB) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a technical documentation analyst. Compare the documentation for '")
          .append(topic).append("' in ").append(product)
          .append(" between version ").append(versionA)
          .append(" and version ").append(versionB).append(".\n\n");

        sb.append("=== Documentation from ").append(versionA).append(" ===\n");
        if (chunksA.isEmpty()) {
            sb.append("(No documentation found for this version)\n");
        } else {
            for (RetrievedChunk c : chunksA) {
                sb.append("[").append(c.getDocumentName()).append("] ").append(c.getContent()).append("\n");
            }
        }

        sb.append("\n=== Documentation from ").append(versionB).append(" ===\n");
        if (chunksB.isEmpty()) {
            sb.append("(No documentation found for this version)\n");
        } else {
            for (RetrievedChunk c : chunksB) {
                sb.append("[").append(c.getDocumentName()).append("] ").append(c.getContent()).append("\n");
            }
        }

        sb.append("""

            Produce a structured comparison with exactly these sections (output the section headers verbatim):
            ADDED:
            (Features, parameters, or behaviors present in %s but not %s. "None" if nothing was added.)

            MODIFIED:
            (Features, parameters, or behaviors that exist in both versions but changed. "None" if nothing changed.)

            REMOVED:
            (Features, parameters, or behaviors present in %s but removed in %s. "None" if nothing was removed.)

            BREAKING_CHANGES:
            (Subset of changes that would break existing integrations or workflows. "None" if no breaking changes.)

            SUMMARY:
            (2-3 sentence plain-language summary of the most important changes.)
            """.formatted(versionB, versionA, versionA, versionB));

        return sb.toString();
    }

    private DiffResult parseDiffResponse(String topic, String product,
                                          String versionA, String versionB, String raw) {
        if (raw == null || raw.isBlank()) {
            return new DiffResult(topic, product, versionA, versionB,
                null, null, null, null, "Unable to generate diff at this time.");
        }

        String added    = extractSection(raw, "ADDED:", "MODIFIED:");
        String modified = extractSection(raw, "MODIFIED:", "REMOVED:");
        String removed  = extractSection(raw, "REMOVED:", "BREAKING_CHANGES:");
        String breaking = extractSection(raw, "BREAKING_CHANGES:", "SUMMARY:");
        String summary  = extractSection(raw, "SUMMARY:", null);

        return new DiffResult(topic, product, versionA, versionB,
            added, modified, removed, breaking, summary);
    }

    private static String extractSection(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start == -1) return null;
        start += startMarker.length();
        int end = endMarker != null ? text.indexOf(endMarker, start) : text.length();
        if (end == -1) end = text.length();
        return text.substring(start, end).trim();
    }

    private String callLlm(String prompt) {
        try {
            return chatClientBuilder.build().prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed in VersionDiffService: {}", e.getMessage());
            return null;
        }
    }
}
