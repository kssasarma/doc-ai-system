package com.docai.ingestor.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.docai.ingestor.domain.model.SemanticChunk;

class SemanticChunkerTest {

    private SemanticChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new SemanticChunker();
        ReflectionTestUtils.setField(chunker, "maxTokens", 50);
        ReflectionTestUtils.setField(chunker, "overlap", 10);
    }

    @Test
    void markdownTable_extractedAsOwnTableChunk_notDilutedIntoText() {
        String table = "| Server | Supported |\n| --- | --- |\n| Tomcat 9 | Yes |\n| JBoss 7 | Yes |";
        String text = "Some intro text.\n\n" + table + "\n\nMore text after.";

        List<SemanticChunk> chunks = chunker.chunk(text);

        List<SemanticChunk> tableChunks = chunks.stream().filter(c -> "TABLE".equals(c.getChunkType())).toList();
        assertThat(tableChunks).hasSize(1);
        assertThat(tableChunks.get(0).getContent()).contains("Tomcat 9").contains("JBoss 7");
        assertThat(tableChunks.get(0).isLeaf()).isTrue();

        // The table's content must not also appear duplicated inside a generic TEXT chunk.
        List<SemanticChunk> textChunks = chunks.stream().filter(c -> "TEXT".equals(c.getChunkType())).toList();
        assertThat(textChunks).noneMatch(c -> c.getContent().contains("Tomcat 9"));
    }

    @Test
    void codeBlock_stillExtractedAsOwnCodeChunk() {
        String text = "Explanation text.\n\n```java\nSystem.out.println(\"hi\");\n```\n\nMore text.";

        List<SemanticChunk> chunks = chunker.chunk(text);

        List<SemanticChunk> codeChunks = chunks.stream().filter(c -> "CODE".equals(c.getChunkType())).toList();
        assertThat(codeChunks).hasSize(1);
        assertThat(codeChunks.get(0).getContent()).contains("System.out.println");
        assertThat(codeChunks.get(0).getCodeLanguage()).isEqualTo("java");
    }

    @Test
    void chunkSplit_carriesOverlapTextIntoNextChunk() {
        ReflectionTestUtils.setField(chunker, "maxTokens", 100);
        ReflectionTestUtils.setField(chunker, "overlap", 15);

        // para1+para2 merge into one ~82-token chunk; para3 then overflows the 100-token budget,
        // forcing a split. The overlap (~15 tokens) carried into the new chunk should include the
        // tail of para2 (where UNIQUE_END_MARKER sits) — comfortably within budget alongside
        // para3, so nothing needs to be dropped.
        String para1 = "Alpha word ".repeat(14) + "FIRST_MARKER";
        String para2 = "Beta word ".repeat(14) + "UNIQUE_END_MARKER";
        String para3 = "Gamma word ".repeat(14) + "THIRD_MARKER";
        String text = para1 + "\n\n" + para2 + "\n\n" + para3;

        List<SemanticChunk> chunks = chunker.chunk(text);
        List<SemanticChunk> leaves = chunks.stream()
            .filter(SemanticChunk::isLeaf)
            .filter(c -> "TEXT".equals(c.getChunkType()))
            .toList();

        assertThat(leaves).hasSizeGreaterThanOrEqualTo(2);
        SemanticChunk second = leaves.get(1);
        assertThat(second.getContent()).contains("THIRD_MARKER");
        assertThat(second.getContent()).contains("UNIQUE_END_MARKER");
    }

    @Test
    void noOverlapConfigured_nextChunkDoesNotCarryPreviousTail() {
        ReflectionTestUtils.setField(chunker, "maxTokens", 100);
        ReflectionTestUtils.setField(chunker, "overlap", 0);

        String para1 = "Alpha word ".repeat(14) + "FIRST_MARKER";
        String para2 = "Beta word ".repeat(14) + "UNIQUE_END_MARKER";
        String para3 = "Gamma word ".repeat(14) + "THIRD_MARKER";
        String text = para1 + "\n\n" + para2 + "\n\n" + para3;

        List<SemanticChunk> chunks = chunker.chunk(text);
        List<SemanticChunk> leaves = chunks.stream()
            .filter(SemanticChunk::isLeaf)
            .filter(c -> "TEXT".equals(c.getChunkType()))
            .toList();

        assertThat(leaves).hasSizeGreaterThanOrEqualTo(2);
        assertThat(leaves.get(1).getContent()).doesNotContain("UNIQUE_END_MARKER");
    }

    @Test
    void overlapWouldExceedBudget_droppedRatherThanTruncatingNewParagraph() {
        // Both paragraphs individually fit comfortably under the token cap on their own, but
        // together (or carry-over + the second paragraph) they don't — carrying overlap forward
        // would push the combined chunk over budget. The new paragraph's own content (including
        // its tail) must survive intact; the overlap is what gets dropped, not silently
        // truncated content from the new paragraph.
        ReflectionTestUtils.setField(chunker, "maxTokens", 60);
        ReflectionTestUtils.setField(chunker, "overlap", 15);

        String para1 = "Alpha word ".repeat(20) + "FIRST_END_MARKER";
        String para2 = "Beta word ".repeat(20) + "SECOND_END_MARKER";
        String text = para1 + "\n\n" + para2;

        List<SemanticChunk> chunks = chunker.chunk(text);
        List<SemanticChunk> leaves = chunks.stream()
            .filter(SemanticChunk::isLeaf)
            .filter(c -> "TEXT".equals(c.getChunkType()))
            .toList();

        assertThat(leaves).hasSizeGreaterThanOrEqualTo(2);
        assertThat(leaves.get(1).getContent()).contains("SECOND_END_MARKER");
    }

    @Test
    void oversizedTable_splitIntoMultipleChunksEachWithinBudget() {
        // maxTokens=50 (from setUp) means ~200 chars per chunk; build a table whose rows alone
        // total well beyond that so it must split rather than stay a single unbounded chunk.
        StringBuilder table = new StringBuilder("| Code | Description |\n| --- | --- |\n");
        for (int i = 0; i < 40; i++) {
            table.append("| ERR-").append(i).append(" | Some longer description of error number ").append(i).append(" |\n");
        }
        String text = "Intro.\n\n" + table + "\nOutro.";

        List<SemanticChunk> chunks = chunker.chunk(text);
        List<SemanticChunk> tableChunks = chunks.stream().filter(c -> "TABLE".equals(c.getChunkType())).toList();

        assertThat(tableChunks.size()).isGreaterThan(1);
        for (SemanticChunk c : tableChunks) {
            assertThat(c.getTokenCount()).isLessThanOrEqualTo(50);
            // Every fragment stays a self-describing table, not orphaned data rows.
            assertThat(c.getContent()).startsWith("| Code | Description |");
        }
        assertThat(tableChunks).anyMatch(c -> c.getContent().contains("ERR-0 "));
        assertThat(tableChunks).anyMatch(c -> c.getContent().contains("ERR-39 "));
    }

    @Test
    void oversizedCodeBlock_splitIntoMultipleChunksEachWithinBudget() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            code.append("System.out.println(\"line number ").append(i).append("\");\n");
        }
        String text = "Explanation.\n\n```java\n" + code + "```\n\nMore text.";

        List<SemanticChunk> chunks = chunker.chunk(text);
        List<SemanticChunk> codeChunks = chunks.stream().filter(c -> "CODE".equals(c.getChunkType())).toList();

        assertThat(codeChunks.size()).isGreaterThan(1);
        for (SemanticChunk c : codeChunks) {
            assertThat(c.getTokenCount()).isLessThanOrEqualTo(50);
            assertThat(c.getCodeLanguage()).isEqualTo("java");
        }
        assertThat(codeChunks).anyMatch(c -> c.getContent().contains("line number 0"));
        assertThat(codeChunks).anyMatch(c -> c.getContent().contains("line number 59"));
    }

    @Test
    void heading_splitsIntoSeparateSection() {
        String text = "# Installation\n\nInstall the agent.\n\n# Configuration\n\nConfigure the server.";

        List<SemanticChunk> chunks = chunker.chunk(text);

        assertThat(chunks).anyMatch(c -> "Installation".equals(c.getSectionHeader()));
        assertThat(chunks).anyMatch(c -> "Configuration".equals(c.getSectionHeader()));
    }
}
