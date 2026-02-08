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
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // Simple token approximation: split by whitespace
        String[] tokens = text.split("\\s+");
        int totalTokens = tokens.length;
        
        log.info("Chunking text with {} tokens into chunks of size {} with overlap {}", 
                 totalTokens, chunkSize, chunkOverlap);

        int index = 0;
        int position = 0;

        while (position < totalTokens) {
            int end = Math.min(position + chunkSize, totalTokens);
            
            StringBuilder chunkContent = new StringBuilder();
            for (int i = position; i < end; i++) {
                if (i > position) {
                    chunkContent.append(" ");
                }
                chunkContent.append(tokens[i]);
            }

            TextChunk chunk = TextChunk.builder()
                .index(index++)
                .content(chunkContent.toString())
                .tokenCount(end - position)
                .build();
            
            chunks.add(chunk);
            
            // Move position forward, accounting for overlap
            position = position + chunkSize - chunkOverlap;
            
            // Ensure we make progress
            if (position <= end - chunkSize && position > 0) {
                position = end;
            }
        }

        log.info("Created {} chunks from text", chunks.size());
        return chunks;
    }
}
