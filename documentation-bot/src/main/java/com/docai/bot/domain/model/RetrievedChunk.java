package com.docai.bot.domain.model;

import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {

    /** Same fenced-code-block marker {@link ExcerptBuilder} uses to avoid splitting a code block —
     * the ingestion pipeline represents source-doc code samples as Markdown fences, so this is the
     * one reliable signal that a chunk actually has a code example in it (as opposed to just prose
     * that happens to mention code). */
    private static final Pattern FENCED_CODE_BLOCK = Pattern.compile("```");

    private String documentId;
    private String documentName;
    private String content;
    private double similarity;
    private String chunkId;
    private String product;
    private String version;

    /** TEXT | CODE | TABLE — the underlying leaf chunk's type (see document-ingestor's
     * SemanticChunker), independent of {@link #content}. Needed because a CODE-type chunk's
     * content is the bare code itself, with the Markdown fence markers already stripped during
     * ingestion — so content alone can't tell a real code chunk apart from prose. */
    private String chunkType;

    /** Whether this chunk actually is (or contains) a code sample, as opposed to prose that merely
     * discusses code. Used to keep answer-format instructions (e.g. "CODE_FIRST") grounded in what
     * was actually retrieved, rather than blindly demanding a code example the source documentation
     * doesn't have. Checks {@link #chunkType} first — a CODE chunk's own content has no fence
     * markers to match on — and falls back to a literal fence scan for any chunk that still has one
     * (e.g. a TEXT chunk whose content wasn't run through the fence-stripping chunker). */
    public boolean containsCode() {
        if ("CODE".equals(chunkType)) return true;
        return content != null && FENCED_CODE_BLOCK.matcher(content).find();
    }
}
