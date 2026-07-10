package com.docai.bot.domain.model;

import java.util.Comparator;

/**
 * Compares dotted version strings ("14.2", "14.10", "14.9.1") by numeric segment rather than
 * lexicographically — plain string sorting puts {@code "14.10"} before {@code "14.9"} because
 * {@code '1' < '9'} as characters, which is wrong for every product that has shipped a
 * double-digit minor/patch release. Non-numeric segments (e.g. "14.2-beta") fall back to
 * lexicographic comparison for that segment only, so this degrades gracefully rather than
 * throwing on unusual version strings.
 */
public final class VersionComparator implements Comparator<String> {

    public static final VersionComparator INSTANCE = new VersionComparator();

    @Override
    public int compare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        String[] partsA = a.split("[.\\-_]");
        String[] partsB = b.split("[.\\-_]");
        int len = Math.max(partsA.length, partsB.length);

        for (int i = 0; i < len; i++) {
            String segA = i < partsA.length ? partsA[i] : "0";
            String segB = i < partsB.length ? partsB[i] : "0";
            int cmp = compareSegment(segA, segB);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private int compareSegment(String a, String b) {
        Integer numA = tryParseInt(a);
        Integer numB = tryParseInt(b);
        if (numA != null && numB != null) return Integer.compare(numA, numB);
        return a.compareTo(b);
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
