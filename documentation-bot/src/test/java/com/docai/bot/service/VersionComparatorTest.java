package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.VersionComparator;

class VersionComparatorTest {

    @Test
    void doubleDigitMinor_sortsNumericallyNotLexicographically() {
        // The exact bug: plain string sort puts "14.10" before "14.9".
        List<String> sorted = List.of("14.9", "14.10", "14.2").stream()
            .sorted(VersionComparator.INSTANCE).toList();
        assertThat(sorted).containsExactly("14.2", "14.9", "14.10");
    }

    @Test
    void findsMaxCorrectly() {
        String max = List.of("1.0", "1.9", "1.10", "2.0").stream()
            .max(VersionComparator.INSTANCE).orElseThrow();
        assertThat(max).isEqualTo("2.0");
    }

    @Test
    void equalVersions_compareEqual() {
        assertThat(VersionComparator.INSTANCE.compare("14.2.0", "14.2.0")).isZero();
    }

    @Test
    void shorterVersionTreatedAsZeroPaddedSuffix() {
        // "14.2" vs "14.2.1" — missing segment treated as 0, so 14.2 < 14.2.1
        assertThat(VersionComparator.INSTANCE.compare("14.2", "14.2.1")).isNegative();
    }

    @Test
    void nonNumericSegment_fallsBackToLexicographicForThatSegment() {
        // Doesn't throw on a non-numeric segment (e.g. a beta/rc suffix)
        int cmp = VersionComparator.INSTANCE.compare("14.2-beta", "14.2-rc");
        assertThat(cmp).isNotZero();
    }

    @Test
    void nullVersions_handledWithoutThrowing() {
        assertThat(VersionComparator.INSTANCE.compare(null, "1.0")).isNegative();
        assertThat(VersionComparator.INSTANCE.compare("1.0", null)).isPositive();
        assertThat(VersionComparator.INSTANCE.compare(null, null)).isZero();
    }
}
