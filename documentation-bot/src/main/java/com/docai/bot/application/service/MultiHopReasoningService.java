package com.docai.bot.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements multi-hop reasoning (Phase 6.1): decomposes a complex question
 * into 2–3 sub-questions, runs independent vector searches for each,
 * and synthesizes the results into a single grounded answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiHopReasoningService {

    private static final int MAX_SUBQUESTIONS = 3;
    private static final String SUB_MARKER = "SUB:";

    private final ChatClient.Builder chatClientBuilder;
    private final VectorSearchService vectorSearchService;

    @Value("${bot.min-similarity-threshold:0.55}")
    private double minSimilarityThreshold;

    public record HopResult(
        String subQuestion,
        List<RetrievedChunk> chunks,
        double maxSimilarity
    ) {}

    public record MultiHopAnswer(
        String answer,
        List<HopResult> hops,
        List<String> relatedQuestions,
        int promptTokens,
        int completionTokens
    ) {}

    /**
     * Determines whether a question is complex enough to warrant multi-hop reasoning.
     * Heuristic: multiple conjunctions, cross-references, or explicit comparisons.
     */
    public boolean isComplexQuery(String question) {
        if (question == null || question.length() < 30) return false;
        String lower = question.toLowerCase();
        int signals = 0;
        if (lower.contains(" and ") && lower.contains(" when ")) signals++;
        if (lower.contains(" interact") || lower.contains(" relationship")) signals++;
        if (lower.contains(" compared to ") || lower.contains(" difference between ")) signals++;
        if (lower.contains(" how does") && lower.contains(" affect ")) signals++;
        if (lower.contains(" both ") || lower.contains(" together ")) signals++;
        if (lower.contains(" with ") && lower.contains(" enabled") && lower.contains(" ?")) signals++;
        return signals >= 1;
    }

    /**
     * Decomposes the question, performs multi-pass retrieval, and synthesizes.
     */
    public MultiHopAnswer reason(String question, String chatContext,
                                  String product, String version,
                                  String verbosity, String answerFormat) {

        List<String> subQuestions = decomposeQuestion(question, product, version);
        log.info("Multi-hop: decomposed '{}' into {} sub-questions", question, subQuestions.size());

        List<HopResult> hops = new ArrayList<>();
        Map<String, List<RetrievedChunk>> subAnswers = new LinkedHashMap<>();

        for (String sub : subQuestions) {
            List<RetrievedChunk> chunks = vectorSearchService.search(sub, product, version);
            double maxSim = chunks.stream().mapToDouble(RetrievedChunk::getSimilarity).max().orElse(0.0);
            hops.add(new HopResult(sub, chunks, maxSim));
            if (maxSim >= minSimilarityThreshold) {
                subAnswers.put(sub, chunks);
            }
        }

        if (subAnswers.isEmpty()) {
            return new MultiHopAnswer(
                "I couldn't find reliable documentation to answer this multi-part question. " +
                "Try asking each part separately.",
                hops, List.of(), 0, 0
            );
        }

        return synthesize(question, chatContext, hops, subAnswers, verbosity, answerFormat);
    }

    private List<String> decomposeQuestion(String question, String product, String version) {
        String productCtx = (product != null ? product : "the product") +
                            (version != null ? " " + version : "");
        String prompt = """
            You are a documentation retrieval expert. Break this complex question into %d or fewer independent sub-questions that can each be answered from a documentation search.

            Product context: %s
            Complex question: %s

            Rules:
            - Each sub-question must be self-contained and answerable independently
            - Sub-questions should cover different aspects of the original question
            - Keep each sub-question concise (under 20 words)
            - Output ONLY the sub-questions, one per line, each starting with "%s"
            - If the question is already simple enough (1 concept), output just 1 sub-question

            Sub-questions:
            """.formatted(MAX_SUBQUESTIONS, productCtx, question, SUB_MARKER);

        try {
            String response = chatClientBuilder.build().prompt().user(prompt).call().content();
            if (response == null || response.isBlank()) return List.of(question);

            List<String> subQs = response.lines()
                .map(String::trim)
                .filter(l -> l.startsWith(SUB_MARKER))
                .map(l -> l.substring(SUB_MARKER.length()).trim())
                .filter(l -> !l.isBlank())
                .limit(MAX_SUBQUESTIONS)
                .collect(Collectors.toList());

            return subQs.isEmpty() ? List.of(question) : subQs;
        } catch (Exception e) {
            log.warn("Sub-question decomposition failed, falling back to original: {}", e.getMessage());
            return List.of(question);
        }
    }

    private MultiHopAnswer synthesize(
            String originalQuestion,
            String chatContext,
            List<HopResult> hops,
            Map<String, List<RetrievedChunk>> subAnswers,
            String verbosity,
            String answerFormat) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a documentation assistant. Answer the user's complex question by synthesizing information from multiple documentation searches.\n\n");

        if ("CONCISE".equals(verbosity)) {
            prompt.append("Be concise: 3-5 sentences maximum.\n");
        } else if ("DETAILED".equals(verbosity)) {
            prompt.append("Be thorough: include full explanation, context, and connections between concepts.\n");
        }
        if ("BULLET_POINTS".equals(answerFormat)) {
            prompt.append("Format using bullet points.\n");
        } else if ("CODE_FIRST".equals(answerFormat)) {
            prompt.append("Lead with a code example if applicable.\n");
        }

        if (chatContext != null && !chatContext.isBlank()) {
            prompt.append("\nConversation context:\n").append(chatContext).append("\n");
        }

        prompt.append("\nThe original complex question: ").append(originalQuestion).append("\n\n");
        prompt.append("Relevant documentation retrieved across multiple searches:\n\n");

        int sourceNum = 1;
        for (Map.Entry<String, List<RetrievedChunk>> entry : subAnswers.entrySet()) {
            prompt.append("Search aspect: \"").append(entry.getKey()).append("\"\n");
            for (RetrievedChunk chunk : entry.getValue()) {
                prompt.append(String.format("[Source %d – %s] %s\n",
                    sourceNum++, chunk.getDocumentName(), chunk.getContent()));
            }
            prompt.append("\n");
        }

        prompt.append("Synthesize a complete, accurate answer to the original question. ");
        prompt.append("Explicitly address how the different aspects relate to each other. ");
        prompt.append("Do not speculate beyond what the documentation says.\n\n");
        prompt.append("After your answer, write exactly: ---FOLLOW-UP-QUESTIONS---\n");
        prompt.append("Then provide 3 follow-up questions, one per line.\n");

        try {
            var chatResponse = chatClientBuilder.build().prompt().user(prompt.toString()).call().chatResponse();
            String content = chatResponse != null && chatResponse.getResult() != null
                ? chatResponse.getResult().getOutput().getText() : null;

            int promptTokens = 0, completionTokens = 0;
            try {
                if (chatResponse != null && chatResponse.getMetadata() != null
                        && chatResponse.getMetadata().getUsage() != null) {
                    var usage = chatResponse.getMetadata().getUsage();
                    promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                    completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
                }
            } catch (Exception ignored) {}

            String answer = content;
            List<String> related = List.of();
            if (content != null) {
                int markerIdx = content.indexOf("---FOLLOW-UP-QUESTIONS---");
                if (markerIdx != -1) {
                    answer = content.substring(0, markerIdx).trim();
                    related = content.substring(markerIdx + 25).lines()
                        .map(String::trim).filter(l -> !l.isBlank()).limit(3).toList();
                }
            }

            return new MultiHopAnswer(
                answer != null ? answer : "Unable to synthesize an answer at this time.",
                hops, related, promptTokens, completionTokens
            );
        } catch (Exception e) {
            log.error("Multi-hop synthesis failed: {}", e.getMessage());
            return new MultiHopAnswer(
                "The AI service is temporarily unavailable. Please try again in a moment.",
                hops, List.of(), 0, 0
            );
        }
    }
}
