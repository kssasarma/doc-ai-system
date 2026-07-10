package com.docai.bot.application.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.model.VersionComparator;
import com.docai.bot.domain.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.5 — Answer Evolution Timeline.
 * Shows how the answer to a question has changed across all available versions of a product.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerEvolutionService {

    private final VectorSearchService vectorSearchService;
    private final ChatClient.Builder chatClientBuilder;
    private final DocumentRepository documentRepository;

    public record VersionSnapshot(
        String version,
        String answer,
        double confidence,
        boolean hasDocumentation
    ) {}

    public record EvolutionTimeline(
        String question,
        String product,
        List<VersionSnapshot> snapshots,
        String breakingSummary
    ) {}

    /** {@code scope} is the caller's own resolved access — every per-version search below stays
     * within it, so the timeline can never surface a version's content the caller can't access. */
    public EvolutionTimeline getEvolution(String question, SearchScope scope, String product) {
        List<String> versions = documentRepository.findVersionsByProduct(product).stream()
            .sorted(VersionComparator.INSTANCE)
            .toList();
        if (versions.isEmpty()) {
            return new EvolutionTimeline(question, product, List.of(),
                "No documentation found for product: " + product);
        }

        log.info("Computing evolution timeline for '{}' across {} versions of {}",
            question, versions.size(), product);

        List<VersionSnapshot> snapshots = new ArrayList<>();
        for (String version : versions) {
            VersionSnapshot snap = buildSnapshot(question, scope, product, version);
            snapshots.add(snap);
        }

        String breakingSummary = snapshots.size() > 1
            ? detectBreakingChanges(question, product, snapshots)
            : null;

        return new EvolutionTimeline(question, product, snapshots, breakingSummary);
    }

    private VersionSnapshot buildSnapshot(String question, SearchScope scope, String product, String version) {
        List<RetrievedChunk> chunks = vectorSearchService.search(question, scope.withVersionNarrow(product, version));
        if (chunks.isEmpty()) {
            return new VersionSnapshot(version, null, 0.0, false);
        }

        double maxSim = chunks.stream().mapToDouble(RetrievedChunk::getSimilarity).max().orElse(0.0);
        if (maxSim < 0.45) {
            return new VersionSnapshot(version, null, maxSim, false);
        }

        String prompt = buildSnapshotPrompt(question, version, chunks);
        String answer = callLlm(prompt);
        return new VersionSnapshot(version, answer, maxSim, true);
    }

    private String buildSnapshotPrompt(String question, String version, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Answer the following question based ONLY on the documentation excerpts provided. ");
        sb.append("Be concise (2-3 sentences). Do not add information not in the documentation.\n\n");
        sb.append("Version: ").append(version).append("\n");
        sb.append("Question: ").append(question).append("\n\n");
        sb.append("Documentation:\n");
        for (RetrievedChunk c : chunks) {
            sb.append(c.getContent()).append("\n");
        }
        sb.append("\nAnswer:");
        return sb.toString();
    }

    private String detectBreakingChanges(String question, String product, List<VersionSnapshot> snapshots) {
        long documented = snapshots.stream().filter(VersionSnapshot::hasDocumentation).count();
        if (documented < 2) return null;

        StringBuilder prompt = new StringBuilder();
        prompt.append("Review these version-by-version answers about '").append(question)
              .append("' in ").append(product).append(".\n");
        prompt.append("Identify any breaking changes or important behavioral differences across versions.\n\n");

        for (VersionSnapshot snap : snapshots) {
            if (snap.hasDocumentation() && snap.answer() != null) {
                prompt.append("Version ").append(snap.version()).append(": ").append(snap.answer()).append("\n\n");
            }
        }

        prompt.append("In 1-2 sentences, summarize the most important changes. ");
        prompt.append("If there are no significant changes, say 'Behavior is consistent across versions.'");

        try {
            return callLlm(prompt.toString());
        } catch (Exception e) {
            log.warn("Breaking change detection failed: {}", e.getMessage());
            return null;
        }
    }

    private String callLlm(String prompt) {
        try {
            return chatClientBuilder.build().prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed in AnswerEvolutionService: {}", e.getMessage());
            return null;
        }
    }
}
