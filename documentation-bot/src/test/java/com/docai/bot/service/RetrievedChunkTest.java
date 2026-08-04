package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.RetrievedChunk;

class RetrievedChunkTest {

    @Test
    void containsCode_fencedCodeBlock_returnsTrue() {
        RetrievedChunk chunk = RetrievedChunk.builder()
            .content("Example:\n```js\nconst fd = new FormData();\n```\nDone.")
            .build();

        assertThat(chunk.containsCode()).isTrue();
    }

    @Test
    void containsCode_plainProse_returnsFalse() {
        RetrievedChunk chunk = RetrievedChunk.builder()
            .content("FormData is a browser API used to build key/value pairs for form submissions.")
            .build();

        assertThat(chunk.containsCode()).isFalse();
    }

    @Test
    void containsCode_nullContent_returnsFalse() {
        RetrievedChunk chunk = RetrievedChunk.builder().content(null).build();

        assertThat(chunk.containsCode()).isFalse();
    }

    @Test
    void containsCode_realCodeChunk_returnsTrueEvenWithoutFenceMarkers() {
        // Mirrors what SemanticChunker actually produces for a CODE-type chunk: the fence markers
        // (```) are stripped during ingestion, leaving only the bare code — so a plain content scan
        // for "```" (the old implementation) never matches a real extracted code chunk. chunkType
        // is the only reliable signal left.
        RetrievedChunk chunk = RetrievedChunk.builder()
            .chunkType("CODE")
            .content("const fd = new FormData();")
            .build();

        assertThat(chunk.containsCode()).isTrue();
    }
}
