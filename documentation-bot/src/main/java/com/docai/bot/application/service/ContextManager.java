package com.docai.bot.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSummary;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSummaryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextManager {

    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;
    private final LLMRouter llmRouter;

    @Value("${bot.max-context-messages:10}")
    private int maxContextMessages;

    /** How many recent messages the rewrite prompt is shown — deliberately small: resolving "it"
     * needs the last exchange or two, not the whole conversation, and a long prompt here is paid
     * on every follow-up. */
    @Value("${bot.context.rewrite-messages:6}")
    private int rewriteContextMessages;

    @Value("${bot.context.rewrite-enabled:true}")
    private boolean rewriteEnabled;

    /** Longest accepted rewrite. A standalone question is a question; anything longer is the
     * model having answered, explained or rambled, and is discarded. */
    @Value("${bot.context.rewrite-max-chars:400}")
    private int rewriteMaxChars;

    public String buildContextPrompt(UUID chatId) {
        StringBuilder context = new StringBuilder();
        
        // Try to get summary first
        Optional<ChatSummary> summary = summaryRepository.findById(chatId);
        if (summary.isPresent()) {
            context.append("Previous conversation summary:\n");
            context.append(summary.get().getSummary());
            context.append("\n\n");
        }
        
        // Get recent messages
        List<ChatMessage> recentMessages = messageRepository
            .findRecentMessages(chatId, maxContextMessages);
        
        if (!recentMessages.isEmpty()) {
            context.append("Recent conversation:\n");
            // Reverse to get chronological order
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                ChatMessage msg = recentMessages.get(i);
                context.append(msg.getRole()).append(": ");
                context.append(msg.getContent()).append("\n");
            }
        }
        
        return context.toString();
    }

    public int getContextMessageCount(UUID chatId) {
        return (int) messageRepository.countByChatId(chatId);
    }

    public String getConversationHistory(UUID chatId, int limit) {
        List<ChatMessage> messages = messageRepository.findRecentMessages(chatId, limit);

        return messages.stream()
            .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));
    }

    // ── Follow-up query rewriting ────────────────────────────────────────────

    /**
     * Turns a follow-up question that only makes sense in context into one retrieval can actually
     * use — "and in staging?" matches documents about staging in general, not about whatever the
     * conversation has actually been discussing. Handing the model a transcript (see
     * {@link #buildContextPrompt}) fixes generation but not this: the vector/lexical search below
     * it still ran on the raw follow-up text, over the wrong chunks, before the model ever saw the
     * conversation. Deliberately does not paste the transcript into the query itself — that dilutes
     * the embedding with hundreds of characters of a previous answer instead of resolving the
     * one or two ambiguous words in the new question.
     *
     * <p>Total function: always returns a usable retrieval string. A rewrite is only attempted when
     * the question shows some sign of depending on prior turns ({@link #dependsOnContext}); any
     * failure — no history yet, the feature disabled, the LLM call erroring, an implausible reply —
     * falls back to the original question, so retrieval is never worse than before this existed.
     */
    public String rewriteForRetrieval(UUID chatId, String question) {
        if (question == null || question.isBlank()) return question;
        if (!rewriteEnabled) return question;
        if (!dependsOnContext(question)) return question;

        List<ChatMessage> recent = messageRepository.findRecentMessages(chatId, rewriteContextMessages);
        if (recent.isEmpty()) return question;

        String transcript = recent.stream()
            .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
            .map(m -> (m.getRole() == ChatMessage.Role.USER ? "User: " : "Assistant: ") + m.getContent())
            .collect(Collectors.joining("\n"));

        try {
            String prompt = "You rewrite a follow-up question into a standalone search query for a documentation search engine.\n\n"
                + "You are given the recent turns of a conversation and the user's latest question. "
                + "Replace every pronoun and every implicit reference in that question with what it refers to "
                + "in the conversation, so the result can be understood entirely on its own.\n\n"
                + "Rules:\n"
                + "- Reply with the rewritten question alone: one line, no quotation marks, no preamble, no explanation.\n"
                + "- Never answer the question. You are rewriting it, not responding to it.\n"
                + "- Keep the user's own terminology, product names and spelling. Only substitute what is ambiguous on its own.\n"
                + "- Add nothing the conversation does not already say. If a reference is unclear, leave it as the user wrote it.\n"
                + "- If the question already stands on its own, reply with it unchanged.\n\n"
                + "Conversation so far:\n" + transcript
                + "\n\nFollow-up question: " + question
                + "\n\nStandalone question:";

            String rewritten = sanitise(llmRouter.chat(prompt, false), question);
            if (!rewritten.equals(question)) {
                log.info("Rewrote follow-up '{}' to '{}' for retrieval", question, rewritten);
            }
            return rewritten;
        } catch (Exception e) {
            log.debug("Could not rewrite follow-up ({}); retrieving with the question as asked", e.getMessage());
            return question;
        }
    }

    /** Words that point at something said earlier rather than naming it. */
    private static final Pattern ANAPHORA = Pattern.compile(
        "\\b(it|its|it's|this|that|these|those|they|them|their|theirs|he|him|his|she|her|hers|"
        + "same|above|former|latter|previous|preceding|earlier|aforementioned)\\b",
        Pattern.CASE_INSENSITIVE);

    /** Openers that continue a thought instead of starting one. */
    private static final Pattern CONTINUATION = Pattern.compile(
        "^(and|but|or|so|then|plus|also|what about|how about|what if|why|why not|ok|okay|yes|no|"
        + "explain|expand|elaborate|clarify|continue|carry on|go on|rephrase|simplify|"
        + "summari[sz]e|compare|more|tell me more|show me more|any (other|others|more))\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern LABEL_PREFIX = Pattern.compile(
        "^(?:standalone question|rewritten question|rewrite|question|query)\\s*[:\\-–]\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern SURROUNDING_QUOTES = Pattern.compile("^[\\s\"'`*]+|[\\s\"'`*]+$");

    /** Below this many words a question is too short to carry its own subject. */
    private static final int SELF_CONTAINED_MIN_WORDS = 6;

    private static final int MIN_REWRITE_LENGTH = 3;

    /**
     * Whether the question shows any sign of leaning on what came before. Tuned to over-rewrite
     * rather than under-rewrite: rewriting a question that didn't need it costs one short call and
     * returns the question unchanged, while failing to rewrite one that did is invisible — the user
     * just gets a confidently wrong answer about the wrong page.
     */
    public static boolean dependsOnContext(String question) {
        String trimmed = question.strip();
        if (trimmed.isEmpty()) return false;

        if (CONTINUATION.matcher(trimmed).find()) return true;
        if (ANAPHORA.matcher(trimmed).find()) return true;

        return countWords(trimmed) < SELF_CONTAINED_MIN_WORDS;
    }

    private static int countWords(String text) {
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                words++;
            }
        }
        return words;
    }

    /**
     * Accepts a reply only if it still looks like a question the user could have typed. The
     * failure this guards against is not a malformed string, it's a plausible one: a model that
     * answers the question, or explains its rewrite, produces text that would be embedded and
     * searched as though the user had asked it. Falling back to the original question is always
     * safe, so anything doubtful is rejected.
     */
    private String sanitise(String reply, String fallback) {
        if (reply == null || reply.isBlank()) return fallback;

        String candidate = reply.strip().lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .findFirst()
            .orElse("");

        candidate = LABEL_PREFIX.matcher(candidate).replaceFirst("");
        candidate = SURROUNDING_QUOTES.matcher(candidate).replaceAll("");

        if (candidate.length() < MIN_REWRITE_LENGTH) return fallback;
        if (candidate.length() > rewriteMaxChars) return fallback;

        return candidate;
    }
}
