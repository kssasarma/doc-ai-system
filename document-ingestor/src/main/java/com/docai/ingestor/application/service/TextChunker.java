package com.docai.ingestor.application.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.ingestor.domain.model.TextChunk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TextChunker {

    @Value("${ingestor.chunk-size:800}")
    private int chunkSize;

    @Value("${ingestor.chunk-overlap:100}")
    private int chunkOverlap;

    public List<TextChunk> chunkText(String text) {
        List<TextChunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String[] tokens = text.split("\\s+");
        int totalTokens = tokens.length;

        log.info("Chunking {} tokens into chunks of {} with overlap {}",
                totalTokens, chunkSize, chunkOverlap);

        int index = 0;
        int position = 0;
        int step = Math.max(1, chunkSize - chunkOverlap);

        while (position < totalTokens) {
            int end = Math.min(position + chunkSize, totalTokens);

            StringBuilder content = new StringBuilder();
            for (int i = position; i < end; i++) {
                if (i > position) content.append(' ');
                content.append(tokens[i]);
            }

            String chunkContent = content.toString().trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(TextChunk.builder()
                    .index(index++)
                    .content(chunkContent)
                    .tokenCount(end - position)
                    .build());
            }

            position += step;
        }

        log.info("Created {} chunks", chunks.size());
        return chunks;
    }
}
