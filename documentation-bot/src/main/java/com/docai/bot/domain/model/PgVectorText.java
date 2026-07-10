package com.docai.bot.domain.model;

/** Parses pgvector's text representation ("[0.1,0.2,...]", as returned by {@code CAST(col AS text)}
 * in a native query projection) back into a plain float array for in-JVM math (MMR, cosine similarity). */
public final class PgVectorText {

    private PgVectorText() {}

    public static float[] parse(String text) {
        if (text == null || text.isBlank()) return new float[0];
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) return new float[0];
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
