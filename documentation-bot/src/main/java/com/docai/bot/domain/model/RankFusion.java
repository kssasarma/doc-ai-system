package com.docai.bot.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion — combines any number of independently-ranked ID lists (e.g. a dense
 * vector-similarity ranking and a lexical full-text ranking) into one fused ranking, without
 * needing the two rankings' scores to be on a comparable scale (cosine similarity and
 * {@code ts_rank} are not). An ID's fused score is the sum of {@code 1/(k+rank)} across every
 * list it appears in (1-based rank) — an ID ranked highly by both signals rises to the top even
 * if neither signal alone considered it the single best match.
 */
public final class RankFusion {

    private static final int DEFAULT_K = 60;

    private RankFusion() {}

    public static List<String> fuse(List<List<String>> rankedIdLists) {
        return fuse(rankedIdLists, DEFAULT_K);
    }

    public static List<String> fuse(List<List<String>> rankedIdLists, int k) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<String> ranked : rankedIdLists) {
            for (int i = 0; i < ranked.size(); i++) {
                scores.merge(ranked.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList();
    }
}
