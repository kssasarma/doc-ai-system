package com.docai.bot.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a citation excerpt from a chunk's full content without cutting mid-code-block or
 * mid-table-row. A blind character-count truncation could slice a fenced code block or a
 * Markdown table row in half, producing a citation excerpt that renders as broken Markdown and
 * hides exactly the structured content (tables, code) most worth citing precisely.
 */
public final class ExcerptBuilder {

    private static final int DEFAULT_TARGET_LENGTH = 240;
    private static final Pattern FENCED_CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s|$)");

    private ExcerptBuilder() {}

    public static String build(String content) {
        return build(content, DEFAULT_TARGET_LENGTH);
    }

    public static String build(String content, int targetLength) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.length() <= targetLength) return trimmed;

        Integer straddlingEnd = findStraddlingBlockEnd(trimmed, targetLength);
        if (straddlingEnd != null) {
            return straddlingEnd >= trimmed.length()
                ? trimmed
                : trimmed.substring(0, straddlingEnd).stripTrailing() + "…";
        }

        // No code block or table straddles the cut point — prefer the last full sentence before
        // targetLength; fall back to the last word boundary if no sentence end is close enough.
        String window = trimmed.substring(0, targetLength);
        Matcher sentenceMatcher = SENTENCE_END.matcher(window);
        int lastSentenceEnd = -1;
        while (sentenceMatcher.find()) lastSentenceEnd = sentenceMatcher.end();
        if (lastSentenceEnd > targetLength / 2) {
            return trimmed.substring(0, lastSentenceEnd).stripTrailing();
        }

        int lastSpace = window.lastIndexOf(' ');
        String cut = lastSpace > targetLength / 2 ? window.substring(0, lastSpace) : window;
        return cut.stripTrailing() + "…";
    }

    /**
     * If a fenced code block or a Markdown table row starts before {@code targetLength} but ends
     * after it, returns the index just past that block's end so the excerpt keeps it intact.
     * Returns {@code null} if nothing straddles the cut point.
     */
    private static Integer findStraddlingBlockEnd(String text, int targetLength) {
        Matcher codeMatcher = FENCED_CODE_BLOCK.matcher(text);
        while (codeMatcher.find()) {
            if (codeMatcher.start() >= targetLength) break;
            if (codeMatcher.end() > targetLength) return codeMatcher.end();
        }

        int lineStart = text.lastIndexOf('\n', Math.min(targetLength, text.length() - 1)) + 1;
        if (lineStart >= text.length() || text.charAt(lineStart) != '|') return null;

        // Inside a Markdown table row — extend to the end of the contiguous block of '|'-led lines.
        int end = text.indexOf('\n', targetLength);
        while (end != -1) {
            int nextLineStart = end + 1;
            if (nextLineStart >= text.length() || text.charAt(nextLineStart) != '|') break;
            end = text.indexOf('\n', nextLineStart);
        }
        return end == -1 ? text.length() : end;
    }
}
