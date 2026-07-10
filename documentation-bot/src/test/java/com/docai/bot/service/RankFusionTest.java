package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.RankFusion;

class RankFusionTest {

    @Test
    void idRankedFirstByBothSignals_risesToTop() {
        List<String> dense = List.of("a", "b", "c");
        List<String> lexical = List.of("a", "c", "b");

        List<String> fused = RankFusion.fuse(List.of(dense, lexical));

        assertThat(fused.get(0)).isEqualTo("a");
    }

    @Test
    void idOnlyInOneList_stillIncludedInFusedResult() {
        List<String> dense = List.of("a", "b");
        List<String> lexical = List.of("c");

        List<String> fused = RankFusion.fuse(List.of(dense, lexical));

        assertThat(fused).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void emptyLists_returnEmptyFusion() {
        List<String> fused = RankFusion.fuse(List.of(List.of(), List.of()));
        assertThat(fused).isEmpty();
    }

    @Test
    void idAgreedOnByBothRankers_outranksIdOnlyTopInOneRanker() {
        // "b" is #1 in dense but absent from lexical; "a" is #2 in dense and #1 in lexical.
        // Agreement across both signals should win over a single strong ranking.
        List<String> dense = List.of("b", "a", "c");
        List<String> lexical = List.of("a", "d", "e");

        List<String> fused = RankFusion.fuse(List.of(dense, lexical));

        assertThat(fused.get(0)).isEqualTo("a");
    }

    @Test
    void singleList_preservesOriginalOrder() {
        List<String> only = List.of("x", "y", "z");
        List<String> fused = RankFusion.fuse(List.of(only));
        assertThat(fused).containsExactly("x", "y", "z");
    }
}
