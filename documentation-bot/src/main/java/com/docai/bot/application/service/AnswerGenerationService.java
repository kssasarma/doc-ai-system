package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerGenerationService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;

    private final ChatClient.Builder chatClientBuilder;

    @Value("${bot.min-similarity-threshold:0.55}")
    private double minSimilarityThreshold;

    public String generateAnswer(String question, String chatContext,
                                 List<RetrievedChunk> relevantChunks) {

        if (relevantChunks == null || relevantChunks.isEmpty()) {
            log.warn("No relevant chunks found for question: {}", question);
            return buildEmptyStateMessage(question, null, null);
        }

        double maxSimilarity = relevantChunks.stream()
            .mapToDouble(RetrievedChunk::getSimilarity)
            .max()
            .orElse(0.0);

        if (maxSimilarity < minSimilarityThreshold) {
            String closestTopic = relevantChunks.get(0).getDocumentName();
            String product = relevantChunks.get(0).getProduct();
            String version = relevantChunks.get(0).getVersion();
            log.warn("Max similarity {} below threshold {} for question: {}",
                String.format("%.3f", maxSimilarity), String.format("%.3f", minSimilarityThreshold), question);
            return buildEmptyStateMessage(question, closestTopic, product + " " + version);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful documentation assistant. ");
        prompt.append("Answer the user's question based only on the provided documentation excerpts.\n\n");

        if (chatContext != null && !chatContext.isEmpty()) {
            prompt.append("Previous conversation context:\n").append(chatContext).append("\n\n");
        }

        prompt.append("Relevant documentation excerpts:\n");
        for (int i = 0; i < relevantChunks.size(); i++) {
            prompt.append(String.format("[Source %d] %s\n", i + 1, relevantChunks.get(i).getContent()));
        }
        prompt.append("\n\nUser question: ").append(question);
        prompt.append("\n\nProvide a clear, accurate answer based solely on the documentation. ");
        prompt.append("If the documentation does not contain enough information, say so explicitly.");

        return callWithRetry(prompt.toString(), question);
    }

    private String callWithRetry(String prompt, String question) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String answer = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
                if (attempt > 1) {
                    log.info("LLM call succeeded on attempt {}", attempt);
                }
                log.info("Generated answer for: {}", question.substring(0, Math.min(60, question.length())));
                return answer;
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM call attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                }
            }
        }
        log.error("All {} LLM attempts failed for question: {}", MAX_ATTEMPTS, question, lastException);
        return "The AI service is temporarily unavailable. Please try again in a moment.";
    }

    private static String buildEmptyStateMessage(String question, String closestTopic, String productVersion) {
        StringBuilder msg = new StringBuilder(
            "I couldn't find reliable documentation for your question");
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
