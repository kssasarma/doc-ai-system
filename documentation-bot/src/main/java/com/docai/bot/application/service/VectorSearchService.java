package com.docai.bot.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.CosineSimilarity;
import com.docai.bot.domain.model.PgVectorText;
import com.docai.bot.domain.model.RankFusion;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.repository.DocumentChunkRepository.HybridCandidate;
import com.docai.bot.domain.repository.DocumentRepository;
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
    private final DocumentRepository documentRepository;
    private final LLMRouter llmRouter;
    private final ReRankingService reRankingService;

    /** Only present when Redis is configured (see EmbeddingCacheService's @ConditionalOnProperty)
     * — absent otherwise, same optional-dependency pattern as RateLimitFilter's ProxyManager. */
    @Autowired(required = false)
    private EmbeddingCacheService embeddingCacheService;

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

        PGvector queryVector = generateEmbedding(query, scope);
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
                    .documentId(c.getDocumentId())
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
     * Embeds the query using the model that actually produced the *documents in scope's*
     * embeddings — not blindly the tenant's current embedding config, which may have changed
     * since those documents were ingested (see {@link com.docai.bot.domain.entity.Document#getEmbeddingModel()}).
     * Falls back to the tenant's current config when no document in scope recorded a model
     * (ingested before that column existed). If documents in scope carry more than one distinct
     * model — a tenant mid-migration between embedding configs — also falls back to the tenant's
     * current config and logs a warning, rather than picking one arbitrarily: neither past model
     * is uniquely "correct" for the whole scope.
     */
    private PGvector generateEmbedding(String text, SearchScope scope) {
        try {
            List<String> modelsInScope = documentRepository
                .findDistinctEmbeddingModelsAccessible(scope.tenantId(), scope.documentIds());

            List<Double> embedding;
            if (modelsInScope.size() == 1) {
                String model = modelsInScope.get(0);
                embedding = cachedEmbed(text, model);
            } else {
                if (modelsInScope.size() > 1) {
                    log.warn("Search scope for tenant {} spans {} distinct embedding models {} — "
                        + "falling back to the tenant's current embedding config for the query",
                        scope.tenantId(), modelsInScope.size(), modelsInScope);
                }
                embedding = llmRouter.embed(text);
            }
            return new PGvector(toFloatArray(embedding));
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /** Cache is keyed on (text, model) — same text+model always yields the same vector regardless
     * of tenant (see EmbeddingCacheService), so a repeated question anywhere costs one embedding
     * API call instead of one per ask. No-ops gracefully to a plain {@link LLMRouter} call when
     * Redis isn't configured. */
    private List<Double> cachedEmbed(String text, String model) {
        if (embeddingCacheService == null) {
            return llmRouter.embed(text, model);
        }
        Optional<List<Double>> cached = embeddingCacheService.get(text, model);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<Double> embedding = llmRouter.embed(text, model);
        embeddingCacheService.put(text, model, embedding);
        return embedding;
    }

    private static float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i).floatValue();
        return result;
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
