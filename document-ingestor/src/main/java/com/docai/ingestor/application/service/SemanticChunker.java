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
 *  1. Split the document into sections at Markdown heading boundaries.
 *  2. Within each section, pull out code blocks and tables as their own dedicated, searchable
 *     chunks — tagged with that section's heading, same as the prose leaves, so a bare endpoint
 *     table or JSON sample never loses the context that says what it actually is.
 *  3. Split what's left of the section into paragraph leaves.
 *  4. Small-to-big: leaf paragraphs are used for search; parent sections provide context.
 *  5. Merge tiny paragraphs into their neighbours to stay within token limits.
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
        int[] index = {0};

        for (Section section : splitIntoSections(text)) {
            // Code and tables are extracted per-section (not globally) specifically so each one
            // inherits section.heading() — a bare JSON response sample or endpoint table is
            // unreadable to both the retriever and the LLM without the heading naming what it is.
            String body = section.body();

            List<SemanticChunk> codeChunks = extractCode(body, section.heading(), index);
            String bodyWithoutCode = FENCED_CODE_BLOCK.matcher(body).replaceAll("\n\n[CODE_BLOCK]\n\n");

            List<SemanticChunk> tableChunks = extractTables(bodyWithoutCode, section.heading(), index);
            String bodyWithoutTables = MARKDOWN_TABLE.matcher(bodyWithoutCode).replaceAll("\n\n[TABLE_BLOCK]\n\n");

            result.addAll(codeChunks);
            result.addAll(tableChunks);

            List<String> paragraphs = splitParagraphs(bodyWithoutTables);
            List<String> merged = mergeTinyParagraphs(paragraphs);
            if (merged.isEmpty()) continue;

            // Create a parent (section) chunk if there are multiple paragraphs
            SemanticChunk parent = null;
            if (merged.size() > 1) {
                String sectionContent = section.heading() != null
                    ? section.heading() + "\n\n" + section.body()
                    : section.body();
                sectionContent = trimToTokens(sectionContent, maxTokens * 2);
                parent = SemanticChunk.builder()
                    .index(index[0]++)
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
                    .index(index[0]++)
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

        log.info("SemanticChunker: {} leaf + {} parent + {} code + {} table chunks from {} chars",
            result.stream().filter(SemanticChunk::isLeaf).count(),
            result.stream().filter(c -> !c.isLeaf()).count(),
            result.stream().filter(c -> "CODE".equals(c.getChunkType())).count(),
            result.stream().filter(c -> "TABLE".equals(c.getChunkType())).count(),
            text.length());

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extracts fenced code blocks from a section body as their own leaf chunks, prefixed with the
     * section heading (same convention as prose leaves) so the raw code is never shown to the
     * retriever or the LLM with no indication of what it's code *for*. */
    private List<SemanticChunk> extractCode(String sectionBody, String heading, int[] index) {
        List<SemanticChunk> chunks = new ArrayList<>();
        Matcher codeMatcher = FENCED_CODE_BLOCK.matcher(sectionBody);
        while (codeMatcher.find()) {
            String lang = codeMatcher.group(1);
            String code = codeMatcher.group(2).trim();
            for (String part : splitLinesToBudget(code)) {
                String content = heading != null ? "[" + heading + "]\n" + part : part;
                chunks.add(SemanticChunk.builder()
                    .index(index[0]++)
                    .content(content)
                    .chunkType("CODE")
                    .codeLanguage(lang.isBlank() ? null : lang)
                    .sectionHeader(heading)
                    .isLeaf(true)
                    .tokenCount(estimateTokens(content))
                    .build());
            }
        }
        return chunks;
    }

    /** Extracts Markdown tables from a section body (with code already stripped) as their own leaf
     * chunks, prefixed with the section heading — a support-matrix or endpoint-list table diluted
     * into a generic paragraph chunk is exactly the kind of factual content a similarity search
     * struggles to surface; keeping it as its own dedicated, searchable, self-describing chunk
     * fixes that. */
    private List<SemanticChunk> extractTables(String sectionBodyWithoutCode, String heading, int[] index) {
        List<SemanticChunk> chunks = new ArrayList<>();
        Matcher tableMatcher = MARKDOWN_TABLE.matcher(sectionBodyWithoutCode);
        while (tableMatcher.find()) {
            String table = tableMatcher.group(1).trim();
            for (String part : splitTableToBudget(table)) {
                String content = heading != null ? "[" + heading + "]\n" + part : part;
                chunks.add(SemanticChunk.builder()
                    .index(index[0]++)
                    .content(content)
                    .chunkType("TABLE")
                    .sectionHeader(heading)
                    .isLeaf(true)
                    .tokenCount(estimateTokens(content))
                    .build());
            }
        }
        return chunks;
    }

    /**
     * Splits into sections at Markdown heading boundaries. Headings are detected on the raw text
     * (so each section's body still carries its own code/tables for {@link #extractCode} and
     * {@link #extractTables} to pull out) — but a heading-shaped line inside a fenced code block
     * (a Python/shell/YAML comment like {@code # Note: ...}) must not be mistaken for a real
     * section boundary, so any heading match falling inside a fenced code block's range is
     * skipped.
     */
    private List<Section> splitIntoSections(String text) {
        List<int[]> codeRanges = new ArrayList<>();
        Matcher codeMatcher = FENCED_CODE_BLOCK.matcher(text);
        while (codeMatcher.find()) {
            codeRanges.add(new int[]{codeMatcher.start(), codeMatcher.end()});
        }

        List<Section> sections = new ArrayList<>();
        Matcher headings = HEADING.matcher(text);

        List<int[]> headingPositions = new ArrayList<>();
        List<String> headingTexts = new ArrayList<>();
        while (headings.find()) {
            if (isInsideAnyRange(headings.start(), codeRanges)) continue;
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

    private static boolean isInsideAnyRange(int pos, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (pos >= range[0] && pos < range[1]) return true;
        }
        return false;
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

    /** A fenced code block has no natural small-unit boundary like paragraphs do, so an oversized
     * one (a large embedded log/config dump) is split by line instead of left as a single unbounded
     * chunk — otherwise its real tokenizer count can dwarf our char/4 estimate and blow past the
     * embedding model's context window on its own, no batching limit can save a single chunk that's
     * already too big. {@code trimToTokens} on each resulting part is the final backstop for the
     * rare single line that's oversized by itself (a minified one-liner, say). */
    private List<String> splitLinesToBudget(String code) {
        if (code.isBlank()) return List.of();
        if (estimateTokens(code) <= maxTokens) return List.of(code);

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            if (!current.isEmpty() && estimateTokens(current.toString()) + estimateTokens(line) > maxTokens) {
                parts.add(trimToTokens(current.toString(), maxTokens));
                current = new StringBuilder();
            }
            if (!current.isEmpty()) current.append("\n");
            current.append(line);
        }
        if (!current.isEmpty()) parts.add(trimToTokens(current.toString(), maxTokens));
        return parts;
    }

    /** Same unbounded-size problem as {@link #splitLinesToBudget} but for tables: a reference table
     * has no row-count ceiling either, so split by row once it exceeds the budget. Each split
     * repeats the header + separator row so every fragment stays a valid, self-describing table
     * instead of orphaned data rows with no column names. */
    private List<String> splitTableToBudget(String table) {
        if (table.isBlank()) return List.of();
        if (estimateTokens(table) <= maxTokens) return List.of(table);

        String[] lines = table.split("\n", -1);
        if (lines.length < 3) return List.of(trimToTokens(table, maxTokens));

        String prefix = lines[0] + "\n" + lines[1];
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder(prefix);
        boolean hasRows = false;
        for (int i = 2; i < lines.length; i++) {
            String row = lines[i];
            if (row.isBlank()) continue;
            if (hasRows && estimateTokens(current.toString()) + estimateTokens(row) > maxTokens) {
                parts.add(trimToTokens(current.toString(), maxTokens));
                current = new StringBuilder(prefix);
                hasRows = false;
            }
            current.append("\n").append(row);
            hasRows = true;
        }
        if (hasRows) parts.add(trimToTokens(current.toString(), maxTokens));
        return parts.isEmpty() ? List.of(trimToTokens(table, maxTokens)) : parts;
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
