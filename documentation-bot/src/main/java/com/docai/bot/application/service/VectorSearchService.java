package com.docai.bot.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.CosineSimilarity;
import com.docai.bot.domain.model.PgVectorText;
import com.docai.bot.domain.model.RankFusion;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.repository.DocumentChunkRepository.ChunkSearchResult;
import com.docai.bot.domain.repository.DocumentChunkRepository.HybridCandidate;
import com.pgvector.PGvector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid retrieval: fuses a dense (pgvector cosine-similarity) candidate pool with a lexical
 * (Postgres full-text) candidate pool via Reciprocal Rank Fusion, then narrows the fused pool to
 * the final result set via {@link ReRankingService} (MMR + optional LLM re-rank).
 *
 * This exists because dense-only retrieval under-ranks short, specific factual queries (e.g.
 * "supported application servers") whenever the matching sentence's embedding gets diluted by
 * surrounding unrelated text in the same chunk — lexical search catches exactly that case, and
 * fusing the two signals (rather than picking one) means neither one's blind spot is fatal.
 *
 * Every candidate's {@code similarity} is recomputed uniformly in Java from its raw embedding —
 * not trusted from whichever query happened to surface it — so a lexical-only hit (one dense
 * search's top-N missed) still carries a real, comparable cosine score rather than a synthetic
 * placeholder; {@link ChatService#calculateConfidence} and the answer-confidence gate depend on
 * that value meaning the same thing regardless of which retrieval path found the chunk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final ReRankingService reRankingService;

    @Value("${bot.top-k-results:7}")
    private int topK;

    @Value("${bot.rerank.candidate-pool-size:25}")
    private int candidatePoolSize;

    /**
     * Searches within the given scope — the sole eligibility gate (see {@link DocumentAccessPolicy}).
     * Short-circuits without generating an embedding if the scope has no accessible documents.
     */
    public List<RetrievedChunk> search(String query, SearchScope scope) {
        if (scope.isEmpty()) {
            log.info("Search scope for tenant {} has zero accessible documents — skipping", scope.tenantId());
            return List.of();
        }

        log.info("Hybrid search for: {} within {} accessible document(s)", query, scope.documentIds().size());

        PGvector queryVector = generateEmbedding(query);
        String embeddingStr = pgVectorToString(queryVector);

        List<HybridCandidate> denseResults = chunkRepository.findTopNDenseAccessible(
            scope.tenantId(), scope.documentIds(), embeddingStr,
            scope.narrowProduct(), scope.narrowVersion(), candidatePoolSize);
        List<HybridCandidate> lexicalResults = chunkRepository.findTopNLexicalAccessible(
            scope.tenantId(), scope.documentIds(), query,
            scope.narrowProduct(), scope.narrowVersion(), candidatePoolSize);

        if (denseResults.isEmpty() && lexicalResults.isEmpty()) {
            log.info("No candidates from either dense or lexical retrieval");
            return List.of();
        }

        List<ReRankingService.ScoredCandidate> fused = fuseAndScore(denseResults, lexicalResults, queryVector.toArray());
        List<RetrievedChunk> reranked = reRankingService.rerank(query, queryVector.toArray(), fused, topK);

        log.info("Hybrid search: {} dense + {} lexical candidates fused → {} final chunks",
            denseResults.size(), lexicalResults.size(), reranked.size());
        return reranked;
    }

    private List<ReRankingService.ScoredCandidate> fuseAndScore(
            List<HybridCandidate> denseResults, List<HybridCandidate> lexicalResults, float[] queryEmbedding) {

        Map<String, HybridCandidate> byId = new LinkedHashMap<>();
        denseResults.forEach(c -> byId.putIfAbsent(c.getChunkId(), c));
        lexicalResults.forEach(c -> byId.putIfAbsent(c.getChunkId(), c));

        List<String> fusedOrder = RankFusion.fuse(List.of(
            denseResults.stream().map(HybridCandidate::getChunkId).toList(),
            lexicalResults.stream().map(HybridCandidate::getChunkId).toList()
        ));

        return fusedOrder.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(c -> {
                float[] embedding = PgVectorText.parse(c.getEmbeddingText());
                RetrievedChunk chunk = RetrievedChunk.builder()
                    .chunkId(c.getChunkId())
                    .content(c.getContent())
                    .documentName(c.getDocumentName())
                    .product(c.getProduct())
                    .version(c.getVersion())
                    .similarity(CosineSimilarity.of(queryEmbedding, embedding))
                    .build();
                return new ReRankingService.ScoredCandidate(chunk, embedding);
            })
            .toList();
    }

    /**
     * @deprecated Pre-dates per-document access control and does not filter by tenant or by
     * anyone's access grants — it exists only for the handful of admin/system-facing services
     * (corpus-wide analysis, scheduled jobs) not yet retrofitted with tenant scoping. Do not use
     * this for any request made on behalf of a specific end user; use {@link #search(String, SearchScope)}.
     */
    @Deprecated
    public List<RetrievedChunk> search(String query, String product, String version) {
        log.info("Searching for: {} in product: {} version: {}", query, product, version);

        String embeddingStr = pgVectorToString(generateEmbedding(query));

        List<ChunkSearchResult> results;
        if (product != null && version != null) {
            results = chunkRepository.findTopKSimilar(product, version, embeddingStr, topK);
        } else if (product != null) {
            results = chunkRepository.findTopKSimilarByProduct(product, embeddingStr, topK);
        } else {
            results = chunkRepository.findTopKSimilarAll(embeddingStr, topK);
        }

        List<RetrievedChunk> chunks = results.stream()
            .map(row -> RetrievedChunk.builder()
                .chunkId(row.getChunkId())
                .content(row.getContent())
                .documentName(row.getDocumentName())
                .similarity(row.getSimilarity())
                .product(row.getProduct())
                .version(row.getVersion())
                .build())
            .toList();
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
