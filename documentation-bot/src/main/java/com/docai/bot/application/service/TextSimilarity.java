package com.docai.bot.application.service;

/** Shared by {@link AutoFaqService} and {@link FaqClusterGenerationService} — pure, stateless
 * tokenizing/similarity helpers used for both clustering incoming queries and picking the most
 * representative question within an already-formed cluster. */
final class TextSimilarity {

    private TextSimilarity() {}

    static String[] tokenize(String text) {
        if (text == null) return new String[0];
        return text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
    }

    static double jaccardSimilarity(String[] a, String[] b) {
        var setA = java.util.Set.of(a);
        var setB = java.util.Set.of(b);
        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }
}
