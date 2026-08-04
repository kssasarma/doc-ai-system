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

    /** Whether this chunk's content contains an actual code sample (a Markdown fenced code block),
     * as opposed to prose that merely discusses code. Used to keep answer-format instructions
     * (e.g. "CODE_FIRST") grounded in what was actually retrieved, rather than blindly demanding a
     * code example the source documentation doesn't have. */
    public boolean containsCode() {
        return content != null && FENCED_CODE_BLOCK.matcher(content).find();
    }
}
