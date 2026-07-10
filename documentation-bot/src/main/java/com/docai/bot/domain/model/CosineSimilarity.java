package com.docai.bot.domain.model;

/** Pure cosine-similarity math over raw embedding vectors — used by re-ranking (MMR) and to give
 * lexical-only retrieval candidates a real, consistent similarity score instead of a synthetic one. */
public final class CosineSimilarity {

    private CosineSimilarity() {}

    public static double of(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
