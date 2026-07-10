package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.PgVectorText;

class PgVectorTextTest {

    @Test
    void parsesStandardPgVectorTextFormat() {
        float[] result = PgVectorText.parse("[0.1,0.2,0.3]");
        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(0.1f, offset(1e-6f));
        assertThat(result[1]).isCloseTo(0.2f, offset(1e-6f));
        assertThat(result[2]).isCloseTo(0.3f, offset(1e-6f));
    }

    @Test
    void handlesWhitespaceAroundValues() {
        float[] result = PgVectorText.parse("[ 1.5 , -2.25 , 3.0 ]");
        assertThat(result).containsExactly(1.5f, -2.25f, 3.0f);
    }

    @Test
    void nullOrBlank_returnsEmptyArray() {
        assertThat(PgVectorText.parse(null)).isEmpty();
        assertThat(PgVectorText.parse("")).isEmpty();
        assertThat(PgVectorText.parse("   ")).isEmpty();
    }

    @Test
    void emptyBrackets_returnsEmptyArray() {
        assertThat(PgVectorText.parse("[]")).isEmpty();
    }

    @Test
    void roundTripsWithCosineSimilarity() {
        float[] a = PgVectorText.parse("[1,0,0]");
        float[] b = PgVectorText.parse("[1,0,0]");
        assertThat(com.docai.bot.domain.model.CosineSimilarity.of(a, b)).isEqualTo(1.0);
    }
}
