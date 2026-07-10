package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.CosineSimilarity;

class CosineSimilarityTest {

    @Test
    void identicalVectors_similarityIsOne() {
        float[] v = {1f, 2f, 3f};
        assertThat(CosineSimilarity.of(v, v)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void orthogonalVectors_similarityIsZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertThat(CosineSimilarity.of(a, b)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void oppositeVectors_similarityIsNegativeOne() {
        float[] a = {1f, 0f};
        float[] b = {-1f, 0f};
        assertThat(CosineSimilarity.of(a, b)).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void mismatchedLengths_returnsZeroRatherThanThrowing() {
        float[] a = {1f, 2f};
        float[] b = {1f, 2f, 3f};
        assertThat(CosineSimilarity.of(a, b)).isZero();
    }

    @Test
    void nullVectors_returnZeroRatherThanThrowing() {
        assertThat(CosineSimilarity.of(null, new float[]{1f})).isZero();
        assertThat(CosineSimilarity.of(new float[]{1f}, null)).isZero();
    }

    @Test
    void zeroVector_returnsZeroRatherThanNaN() {
        float[] zero = {0f, 0f};
        float[] other = {1f, 1f};
        assertThat(CosineSimilarity.of(zero, other)).isZero();
    }
}
