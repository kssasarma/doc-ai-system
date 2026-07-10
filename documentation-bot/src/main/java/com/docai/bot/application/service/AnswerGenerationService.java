package com.docai.bot.application.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AnswerGenerationService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final String FOLLOW_UP_MARKER = "---FOLLOW-UP-QUESTIONS---";
    private static final String LLM_UNAVAILABLE =
        "The AI service is temporarily unavailable. Please try again in a moment.";

    private final ChatClient.Builder chatClientBuilder;
    private final CircuitBreaker llmCircuitBreaker;
    private final Bulkhead llmBulkhead;

    @Value("${bot.min-similarity-threshold:0.55}")
    private double minSimilarityThreshold;

    public AnswerGenerationService(ChatClient.Builder chatClientBuilder,
                                   @Qualifier("llmCircuitBreaker") CircuitBreaker llmCircuitBreaker,
                                   @Qualifier("llmBulkhead") Bulkhead llmBulkhead) {
        this.chatClientBuilder = chatClientBuilder;
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.llmBulkhead = llmBulkhead;
    }

    public record AnswerResult(String answer, List<String> relatedQuestions,
                               int promptTokens, int completionTokens) {}

    private record LlmResult(String content, int promptTokens, int completionTokens) {}

    /** @deprecated use the {@code product}/{@code version} overload — kept for callers (e.g.
     * {@code AutoFaqService}'s canonical-question generation) that have no meaningful version
     * context to supply; behaves identically to passing {@code null} for both. */
    @Deprecated
    public AnswerResult generateAnswer(String question, String chatContext,
                                       List<RetrievedChunk> relevantChunks,
                                       String verbosity, String answerFormat) {
        return generateAnswer(question, chatContext, relevantChunks, verbosity, answerFormat, null, null);
    }

    /**
     * @param product descriptive product context for the prompt (not an eligibility filter —
     *                that's {@link SearchScope}); null if unknown.
     * @param version descriptive version context for the prompt; null if unknown.
     */
    public AnswerResult generateAnswer(String question, String chatContext,
                                       List<RetrievedChunk> relevantChunks,
                                       String verbosity, String answerFormat,
                                       String product, String version) {

        if (relevantChunks == null || relevantChunks.isEmpty()) {
            log.warn("No relevant chunks found for question: {}", question);
            return new AnswerResult(buildEmptyStateMessage(question, null, null), Collections.emptyList(), 0, 0);
        }

        double maxSimilarity = relevantChunks.stream()
            .mapToDouble(RetrievedChunk::getSimilarity)
            .max()
            .orElse(0.0);

        // Graceful degradation, not a hard refusal: a weak top score doesn't mean the retrieved
        // chunks are useless — it often just means the match is real but partial (e.g. a fact
        // that's present but surrounded by unrelated text). Always let the LLM see what was
        // retrieved; below the threshold, tell it to be explicit about the weak match rather than
        // silently either bluffing confidence or refusing outright. The canned refusal above is
        // reserved for the genuine zero-candidate case.
        boolean lowConfidence = maxSimilarity < minSimilarityThreshold;
        if (lowConfidence) {
            log.info("Max similarity {} below threshold {} for question: {} — answering with a confidence caveat",
                String.format("%.3f", maxSimilarity), String.format("%.3f", minSimilarityThreshold), question);
        }

        // Phase 7: the retrieved chunks may span more than one version of the product (product/
        // version narrow the scope but never gate it — SearchScope's access grant is the only
        // hard filter) — when they do, the LLM must say so explicitly rather than silently
        // answering from whichever chunk happened to score highest.
        boolean spansMultipleVersions = relevantChunks.stream()
            .map(RetrievedChunk::getVersion)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count() > 1;

        String prompt = buildPrompt(question, chatContext, relevantChunks, verbosity, answerFormat,
            lowConfidence, product, version, spansMultipleVersions);
        LlmResult llm = callWithRetry(prompt, question);
        AnswerResult parsed = parseOutput(llm.content());
        return new AnswerResult(parsed.answer(), parsed.relatedQuestions(),
            llm.promptTokens(), llm.completionTokens());
    }

    public String generateSessionTitle(String question, String answer) {
        String prompt = "Generate a concise 5-7 word title for a chat session that started with this question and answer.\n\n" +
            "Question: " + question + "\n" +
            "Answer summary: " + answer.substring(0, Math.min(300, answer.length())) + "\n\n" +
            "Rules:\n" +
            "- 5 to 7 words only\n" +
            "- No quotes, no punctuation at end\n" +
            "- Should describe the topic, not the action\n" +
            "- Example: 'Installing Case360 on Windows Server'\n\n" +
            "Title:";

        try {
            String title = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();
            if (title != null) {
                title = title.trim().replaceAll("^[\"']|[\"']$", "");
                if (!title.isBlank()) return title;
            }
        } catch (Exception e) {
            log.warn("Title generation failed: {}", e.getMessage());
        }
        return question.length() > 60 ? question.substring(0, 60) + "…" : question;
    }

    private String buildPrompt(String question, String chatContext, List<RetrievedChunk> chunks,
                                String verbosity, String answerFormat, boolean lowConfidence,
                                String product, String version, boolean spansMultipleVersions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful documentation assistant. ");
        prompt.append("Answer the user's question based only on the provided documentation excerpts.\n");

        if (product != null || version != null) {
            prompt.append("The user is asking about ")
                  .append(product != null ? product : "the product")
                  .append(version != null ? " version " + version : "")
                  .append(".\n");
        }

        if (spansMultipleVersions) {
            prompt.append("The excerpts below come from more than one version of this product. ");
            prompt.append("If the documented behavior differs between versions, say so explicitly — name ");
            prompt.append("which version each behavior applies to — rather than silently answering from only ");
            prompt.append("one version's excerpt.\n");
        }

        if (lowConfidence) {
            prompt.append("These excerpts are only a weak match for the question — they may be tangential ");
            prompt.append("or only partially relevant. If they don't actually answer the question, say so ");
            prompt.append("explicitly and describe what they DO cover instead of presenting a weak match as ");
            prompt.append("a confident answer.\n");
        }

        if ("CONCISE".equals(verbosity)) {
            prompt.append("Be concise: 2-3 sentences maximum.\n");
        } else if ("DETAILED".equals(verbosity)) {
            prompt.append("Be thorough: include full explanation, context, and examples.\n");
        }

        if ("BULLET_POINTS".equals(answerFormat)) {
            prompt.append("Format your answer as a bullet-point list.\n");
        } else if ("CODE_FIRST".equals(answerFormat)) {
            prompt.append("Lead with a code example, then explain.\n");
        }

        prompt.append("\n");

        if (chatContext != null && !chatContext.isEmpty()) {
            prompt.append("Previous conversation context:\n").append(chatContext).append("\n\n");
        }

        prompt.append("Relevant documentation excerpts:\n");
        for (int i = 0; i < chunks.size(); i++) {
            prompt.append(String.format("[Source %d] %s\n", i + 1, chunks.get(i).getContent()));
        }

        prompt.append("\nUser question: ").append(question);
        prompt.append("\n\nProvide a clear, accurate answer based solely on the documentation. ");
        prompt.append("If the documentation does not contain enough information, say so explicitly.");
        prompt.append("\n\nAfter your answer, on a new line write exactly: ").append(FOLLOW_UP_MARKER);
        prompt.append("\nThen provide exactly 3 short follow-up questions the user might want to ask next, one per line, no numbering.");

        return prompt.toString();
    }

    private AnswerResult parseOutput(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new AnswerResult("The AI service is temporarily unavailable. Please try again.", Collections.emptyList(), 0, 0);
        }

        int markerIdx = rawOutput.indexOf(FOLLOW_UP_MARKER);
        if (markerIdx == -1) {
            return new AnswerResult(rawOutput.trim(), Collections.emptyList(), 0, 0);
        }

        String answer = rawOutput.substring(0, markerIdx).trim();
        String followUpSection = rawOutput.substring(markerIdx + FOLLOW_UP_MARKER.length()).trim();

        List<String> relatedQuestions = Arrays.stream(followUpSection.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .limit(3)
            .toList();

        return new AnswerResult(answer, relatedQuestions, 0, 0);
    }

    /**
     * Calls the LLM with retry (up to MAX_ATTEMPTS), with each attempt protected by
     * the circuit breaker (opens after 5/10 failures) and bulkhead (max 5 concurrent calls).
     * Returns a degraded message instead of throwing on permanent failure.
     */
    private LlmResult callWithRetry(String prompt, String question) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                LlmResult result = callProtectedLlm(prompt);
                if (attempt > 1) log.info("LLM call succeeded on attempt {}", attempt);
                log.info("Generated answer for: {}", question.substring(0, Math.min(60, question.length())));
                return result;
            } catch (CallNotPermittedException e) {
                log.error("LLM circuit breaker is OPEN — serving degraded response for: {}", question);
                return new LlmResult(LLM_UNAVAILABLE, estimateTokens(prompt), 0);
            } catch (BulkheadFullException e) {
                log.error("LLM bulkhead full ({} concurrent calls) — serving degraded response", e.getMessage());
                return new LlmResult(LLM_UNAVAILABLE, estimateTokens(prompt), 0);
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM call attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) sleepBackoff(attempt);
            }
        }
        log.error("All {} LLM attempts failed for question: {}", MAX_ATTEMPTS, question, lastException);
        return new LlmResult(LLM_UNAVAILABLE, estimateTokens(prompt), 0);
    }

    /** Single LLM call wrapped in bulkhead → circuit breaker. Throws on any failure. */
    private LlmResult callProtectedLlm(String prompt) {
        return llmBulkhead.executeSupplier(
            () -> llmCircuitBreaker.executeSupplier(() -> callLlmOnce(prompt))
        );
    }

    /** Bare LLM call — no resilience logic. Throws on any failure so decorators can react. */
    private LlmResult callLlmOnce(String prompt) {
        ChatResponse chatResponse = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .call()
            .chatResponse();

        String content = chatResponse != null && chatResponse.getResult() != null
            ? chatResponse.getResult().getOutput().getText() : null;

        int promptTokens;
        int completionTokens;
        try {
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                promptTokens = usage.getPromptTokens() != null
                    ? usage.getPromptTokens().intValue() : estimateTokens(prompt);
                completionTokens = usage.getCompletionTokens() != null
                    ? usage.getCompletionTokens().intValue() : estimateTokens(content);
            } else {
                promptTokens = estimateTokens(prompt);
                completionTokens = estimateTokens(content);
            }
        } catch (Exception ex) {
            promptTokens = estimateTokens(prompt);
            completionTokens = estimateTokens(content);
        }
        return new LlmResult(content, promptTokens, completionTokens);
    }

    private static int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    private static String buildEmptyStateMessage(String question, String closestTopic, String productVersion) {
        StringBuilder msg = new StringBuilder("I couldn't find reliable documentation for your question");
        if (productVersion != null && !productVersion.isBlank()) {
            msg.append(" in ").append(productVersion);
        }
        msg.append(".\n\n");
        if (closestTopic != null) {
            msg.append("The closest content I found was in **").append(closestTopic).append("**.\n\n");
        }
        msg.append("You might try:\n");
        msg.append("- Rephrasing your question with different keywords\n");
        msg.append("- Checking that the correct product and version are selected\n");
        msg.append("- Browsing the available documents for this product");
        return msg.toString();
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
