package com.docai.bot.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.pgvector.PGvector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${bot.top-k-results:7}")
    private int topK;

    public List<RetrievedChunk> search(String query, String product, String version) {
        log.info("Searching for: {} in product: {} version: {}", query, product, version);
        
        // Generate query embedding
        PGvector queryEmbedding = generateEmbedding(query);
        
        // Convert to string format for PostgreSQL
        String embeddingStr = pgVectorToString(queryEmbedding);
        
        // Execute vector search
        List<Object[]> results;
        if (product != null && version != null) {
            results = chunkRepository.findTopKSimilar(product, version, embeddingStr, topK);
        } else if (product != null) {
            results = chunkRepository.findTopKSimilarByProduct(product, embeddingStr, topK);
        } else {
            results = chunkRepository.findTopKSimilarAll(embeddingStr, topK);
        }
        
        // Convert results to RetrievedChunk objects
        // Query returns: dc.* (based on table DDL: id, chunk_index, content, created_at, document_id, embedding, token_count)
        // plus d.product, d.version, d.document_name
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (Object[] row : results) {
            UUID chunkId = (UUID) row[0];
            String content = (String) row[2];  // content is at index 2
            String documentName = (String) row[9];  // document_name is at index 9
            
            RetrievedChunk chunk = RetrievedChunk.builder()
                .chunkId(chunkId.toString())
                .content(content)
                .documentName(documentName)
                .similarity(0.0) // Cosine distance from pgvector
                .build();
            chunks.add(chunk);
        }
        
        log.info("Found {} relevant chunks", chunks.size());
        return chunks;
    }

    private PGvector generateEmbedding(String text) {
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            if (response.getResults().isEmpty()) {
                throw new RuntimeException("No embedding generated");
            }
            
            float[] embedding = response.getResult().getOutput();
            
            return new PGvector(embedding);
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    private String pgVectorToString(PGvector vector) {
        float[] values = vector.toArray();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
