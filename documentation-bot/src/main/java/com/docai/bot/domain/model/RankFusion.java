package com.docai.bot.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return List.copyOf(fuseWithScores(rankedIdLists, k).keySet());
    }

    /**
     * Same fusion as {@link #fuse}, but keeps each ID's numeric RRF score instead of discarding
     * it once the order is decided. Callers that only need a ranking should use {@link #fuse};
     * this exists for callers (e.g. re-ranking) that need to know *how strongly* an ID was agreed
     * on by the fused signals, not just where it landed relative to its neighbors — an ID that's
     * rank 1 in one list and absent from the other still carries a real, comparable score here,
     * rather than that agreement-strength information being lost the moment fusion produces an
     * ordering.
     */
    public static Map<String, Double> fuseWithScores(List<List<String>> rankedIdLists) {
        return fuseWithScores(rankedIdLists, DEFAULT_K);
    }

    public static Map<String, Double> fuseWithScores(List<List<String>> rankedIdLists, int k) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<String> ranked : rankedIdLists) {
            for (int i = 0; i < ranked.size(); i++) {
                scores.merge(ranked.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> a, LinkedHashMap::new));
    }
}
