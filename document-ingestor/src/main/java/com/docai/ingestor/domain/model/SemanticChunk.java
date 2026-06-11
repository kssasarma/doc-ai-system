package com.docai.ingestor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticChunk {
    private int index;
    private String content;
    private int tokenCount;

    /** TEXT | CODE | TABLE | HEADER */
    @Builder.Default
    private String chunkType = "TEXT";

    /** Index of the parent (section-level) chunk, or null for top-level chunks. */
    private Integer parentChunkIndex;

    private String sectionHeader;
    private Integer pageNumber;
    private String codeLanguage;

    /** True = leaf paragraph (used for search); false = section-level context chunk. */
    @Builder.Default
    private boolean isLeaf = true;
}
