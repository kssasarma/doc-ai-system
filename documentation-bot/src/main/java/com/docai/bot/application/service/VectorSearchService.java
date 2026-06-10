package com.docai.bot.application.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.repository.DocumentChunkRepository.ChunkSearchResult;
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

        PGvector queryEmbedding = generateEmbedding(query);
        String embeddingStr = pgVectorToString(queryEmbedding);

        List<ChunkSearchResult> results;
        if (product != null && version != null) {
            results = chunkRepository.findTopKSimilar(product, version, embeddingStr, topK);
        } else if (product != null) {
            results = chunkRepository.findTopKSimilarByProduct(product, embeddingStr, topK);
        } else {
            results = chunkRepository.findTopKSimilarAll(embeddingStr, topK);
        }

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (ChunkSearchResult row : results) {
            chunks.add(RetrievedChunk.builder()
                .chunkId(row.getChunkId())
                .content(row.getContent())
                .documentName(row.getDocumentName())
                .similarity(row.getSimilarity())
                .product(row.getProduct())
                .version(row.getVersion())
                .build());
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

            return new PGvector(response.getResult().getOutput());
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
