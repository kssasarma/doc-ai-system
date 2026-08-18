package com.docai.bot.application.service;

import com.docai.bot.domain.model.CosineSimilarity;
import java.util.ArrayList;
import java.util.List;

/**
 * Greedy semantic clustering over pre-computed query embeddings using cosine similarity.
 *
 * Using cosine rather than lexical Jaccard means paraphrases — "how do I reset my password"
 * vs "forgot my credentials, need help" — land in the same cluster even when they share no
 * tokens. The greedy O(n²) algorithm is sufficient for the weekly-batch volume (typically
 * hundreds of queries per tenant-product partition, never millions).
 *
 * The caller is responsible for ensuring {@code embeddings} and the source list are parallel
 * arrays of the same length. If embedding fails upstream, {@link AutoFaqService} falls back to
 * lexical Jaccard clustering so a transient LLM error never skips a whole generation run.
 */
final class SemanticClusterer {

    private SemanticClusterer() {}

    /**
     * Clusters embeddings by cosine similarity.
     *
     * @param embeddings  one {@code float[]} per query, in the same order as the source list
     * @param threshold   minimum cosine similarity for two queries to be co-clustered
     * @return            clusters as lists of indices into the input arrays; every index appears
     *                    in exactly one cluster (singleton clusters are included so callers can
     *                    apply their own minimum-size filter)
     */
    static List<List<Integer>> cluster(float[][] embeddings, double threshold) {
        boolean[] assigned = new boolean[embeddings.length];
        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < embeddings.length; i++) {
            if (assigned[i]) continue;
            List<Integer> cluster = new ArrayList<>();
            cluster.add(i);
            assigned[i] = true;
            for (int j = i + 1; j < embeddings.length; j++) {
                if (assigned[j]) continue;
                if (CosineSimilarity.of(embeddings[i], embeddings[j]) >= threshold) {
                    cluster.add(j);
                    assigned[j] = true;
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }
}
