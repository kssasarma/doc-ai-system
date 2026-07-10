package com.docai.ingestor.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.ingestor.domain.model.SemanticChunk;

import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.7 — Semantic Chunking v2.
 *
 * Strategy:
 *  1. Detect code blocks (``` fenced or 4-space indented) → separate CODE chunks
 *  2. Split remaining text at semantic boundaries (blank lines, Markdown/heading patterns)
 *  3. Build parent (section) chunks and child (paragraph) chunks
 *  4. Small-to-big: leaf paragraphs are used for search; parent sections provide context
 *  5. Merge tiny paragraphs into their neighbours to stay within token limits
 */
@Slf4j
@Service
public class SemanticChunker {

    private static final Pattern FENCED_CODE_BLOCK =
        Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    // A Markdown pipe table: a header row, a separator row (only -, :, |, and whitespace), then
    // zero or more further rows — matches what HtmlToMarkdownConverter emits for <table> elements.
    private static final Pattern MARKDOWN_TABLE = Pattern.compile(
        "^(\\|.*\\|[ \\t]*\\n\\|[ \\t:|-]+\\|[ \\t]*\\n(?:\\|.*\\|[ \\t]*\\n?)*)", Pattern.MULTILINE);

    private static final Pattern HEADING =
        Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    private static final Pattern BLANK_LINE_SEPARATOR =
        Pattern.compile("\\n{2,}");

    @Value("${ingestor.chunk-size:800}")
    private int maxTokens;

    @Value("${ingestor.chunk-overlap:100}")
    private int overlap;

    public List<SemanticChunk> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<SemanticChunk> result = new ArrayList<>();

        // 1. Extract and replace code blocks
        Matcher codeMatcher = FENCED_CODE_BLOCK.matcher(text);
        List<int[]> codeRanges = new ArrayList<>();
        List<SemanticChunk> codeChunks = new ArrayList<>();
        int codeIndex = 0;

        while (codeMatcher.find()) {
            String lang = codeMatcher.group(1);
            String code = codeMatcher.group(2).trim();
            if (!code.isBlank()) {
                codeChunks.add(SemanticChunk.builder()
                    .index(codeIndex++)
                    .content(code)
                    .chunkType("CODE")
                    .codeLanguage(lang.isBlank() ? null : lang)
                    .isLeaf(true)
                    .tokenCount(estimateTokens(code))
                    .build());
            }
            codeRanges.add(new int[]{codeMatcher.start(), codeMatcher.end()});
        }

        // 2. Remove code blocks from text before semantic splitting
        String textWithoutCode = FENCED_CODE_BLOCK.matcher(text).replaceAll("\n\n[CODE_BLOCK]\n\n");

        // 2b. Extract Markdown tables the same way — a support-matrix table diluted into a
        // generic paragraph chunk is exactly the kind of factual content a similarity search
        // struggles to surface; keeping it as its own dedicated, searchable chunk fixes that.
        Matcher tableMatcher = MARKDOWN_TABLE.matcher(textWithoutCode);
        List<SemanticChunk> tableChunks = new ArrayList<>();
        int tableIndex = codeIndex;
        while (tableMatcher.find()) {
            String table = tableMatcher.group(1).trim();
            if (!table.isBlank()) {
                tableChunks.add(SemanticChunk.builder()
                    .index(tableIndex++)
                    .content(table)
                    .chunkType("TABLE")
                    .isLeaf(true)
                    .tokenCount(estimateTokens(table))
                    .build());
            }
        }
        String textWithoutTables = MARKDOWN_TABLE.matcher(textWithoutCode).replaceAll("\n\n[TABLE_BLOCK]\n\n");

        // 3. Split into sections by headings
        List<Section> sections = splitIntoSections(textWithoutTables);

        // 4. For each section, further split paragraphs into leaf chunks
        int leafIndex = tableIndex;
        for (Section section : sections) {
            List<String> paragraphs = splitParagraphs(section.body());
            List<String> merged = mergeTinyParagraphs(paragraphs);

            if (merged.isEmpty()) continue;

            // Create a parent (section) chunk if there are multiple paragraphs
            SemanticChunk parent = null;
            if (merged.size() > 1) {
                String sectionContent = section.heading() != null
                    ? section.heading() + "\n\n" + section.body()
                    : section.body();
                // Trim section to max tokens
                sectionContent = trimToTokens(sectionContent, maxTokens * 2);
                parent = SemanticChunk.builder()
                    .index(leafIndex++)
                    .content(sectionContent)
                    .chunkType("TEXT")
                    .sectionHeader(section.heading())
                    .isLeaf(false)
                    .tokenCount(estimateTokens(sectionContent))
                    .build();
                result.add(parent);
            }

            for (String para : merged) {
                String content = section.heading() != null
                    ? "[" + section.heading() + "]\n" + para
                    : para;
                content = trimToTokens(content, maxTokens);

                SemanticChunk leaf = SemanticChunk.builder()
                    .index(leafIndex++)
                    .content(content)
                    .chunkType("TEXT")
                    .sectionHeader(section.heading())
                    .parentChunkIndex(parent != null ? parent.getIndex() : null)
                    .isLeaf(true)
                    .tokenCount(estimateTokens(content))
                    .build();
                result.add(leaf);
            }
        }

        // 5. Append code and table chunks at the end (they reference no parent)
        result.addAll(codeChunks);
        result.addAll(tableChunks);

        log.info("SemanticChunker: {} leaf + {} parent + {} code + {} table chunks from {} chars",
            result.stream().filter(SemanticChunk::isLeaf).count(),
            result.stream().filter(c -> !c.isLeaf()).count(),
            codeChunks.size(),
            tableChunks.size(),
            text.length());

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Section> splitIntoSections(String text) {
        List<Section> sections = new ArrayList<>();
        Matcher headings = HEADING.matcher(text);

        List<int[]> headingPositions = new ArrayList<>();
        List<String> headingTexts = new ArrayList<>();
        while (headings.find()) {
            headingPositions.add(new int[]{headings.start(), headings.end()});
            headingTexts.add(headings.group(2).trim());
        }

        if (headingPositions.isEmpty()) {
            // No headings: treat entire text as one section
            sections.add(new Section(null, text.trim()));
            return sections;
        }

        // Text before first heading
        int firstHeadingStart = headingPositions.get(0)[0];
        if (firstHeadingStart > 0) {
            String preamble = text.substring(0, firstHeadingStart).trim();
            if (!preamble.isBlank()) {
                sections.add(new Section(null, preamble));
            }
        }

        for (int i = 0; i < headingPositions.size(); i++) {
            int bodyStart = headingPositions.get(i)[1];
            int bodyEnd = (i + 1 < headingPositions.size())
                ? headingPositions.get(i + 1)[0]
                : text.length();
            String body = text.substring(bodyStart, bodyEnd).trim();
            sections.add(new Section(headingTexts.get(i), body));
        }

        return sections;
    }

    private List<String> splitParagraphs(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] parts = BLANK_LINE_SEPARATOR.split(text.trim());
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isBlank() && !trimmed.equals("[CODE_BLOCK]") && !trimmed.equals("[TABLE_BLOCK]")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Merges paragraphs up to {@code maxTokens} per chunk. Each new chunk (after the first)
     * starts with the last {@code overlap} tokens' worth of text carried over from the end of the
     * previous chunk, so a fact split across a chunk boundary still has surrounding context in
     * both resulting chunks instead of being orphaned in whichever half it happened to land in.
     */
    private List<String> mergeTinyParagraphs(List<String> paragraphs) {
        if (paragraphs.isEmpty()) return List.of();
        int minTokens = Math.max(30, maxTokens / 8);
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.isEmpty()) {
                current.append(para);
            } else if (estimateTokens(para) < minTokens) {
                current.append("\n\n").append(para);
            } else if (estimateTokens(current.toString()) + estimateTokens(para) <= maxTokens) {
                current.append("\n\n").append(para);
            } else {
                merged.add(current.toString());
                String carryOver = tailOverlap(current.toString());
                // If the new paragraph alone already fills (or nearly fills) the token budget,
                // dropping the carry-over here is the right trade — the alternative is
                // `trimToTokens` downstream silently truncating the *new* paragraph's own tail
                // to make room for the overlap, which would lose real content to keep supplementary
                // context.
                if (!carryOver.isEmpty() && estimateTokens(carryOver) + estimateTokens(para) > maxTokens) {
                    carryOver = "";
                }
                current = new StringBuilder(carryOver.isEmpty() ? para : carryOver + "\n\n" + para);
            }
        }
        if (!current.isEmpty()) merged.add(current.toString());
        return merged;
    }

    /** Last ~{@code overlap} tokens of {@code text}, cut at a word boundary. */
    private String tailOverlap(String text) {
        if (overlap <= 0) return "";
        int charLimit = overlap * 4;
        if (text.length() <= charLimit) return text;
        String tail = text.substring(text.length() - charLimit);
        int firstSpace = tail.indexOf(' ');
        return (firstSpace > 0 && firstSpace < tail.length() - 1) ? tail.substring(firstSpace + 1) : tail;
    }

    private static String trimToTokens(String text, int limit) {
        if (estimateTokens(text) <= limit) return text;
        int charLimit = limit * 4;
        if (text.length() <= charLimit) return text;
        // Trim at last space to avoid cutting mid-word
        String trimmed = text.substring(0, charLimit);
        int lastSpace = trimmed.lastIndexOf(' ');
        return lastSpace > charLimit / 2 ? trimmed.substring(0, lastSpace) + "…" : trimmed + "…";
    }

    static int estimateTokens(String text) {
        return text == null ? 0 : (int) Math.ceil(text.length() / 4.0);
    }

    private record Section(String heading, String body) {}
}
