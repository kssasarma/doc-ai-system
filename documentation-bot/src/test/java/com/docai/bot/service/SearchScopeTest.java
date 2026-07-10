package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.SearchScope;

class SearchScopeTest {

    @Test
    void twoArgConstructor_defaultsNarrowToNull() {
        UUID tenantId = UUID.randomUUID();
        Set<UUID> docs = Set.of(UUID.randomUUID());

        SearchScope scope = new SearchScope(tenantId, docs);

        assertThat(scope.narrowProduct()).isNull();
        assertThat(scope.narrowVersion()).isNull();
        assertThat(scope.hasVersionNarrow()).isFalse();
    }

    @Test
    void withVersionNarrow_preservesTenantAndDocumentIds() {
        UUID tenantId = UUID.randomUUID();
        Set<UUID> docs = Set.of(UUID.randomUUID());
        SearchScope base = new SearchScope(tenantId, docs);

        SearchScope narrowed = base.withVersionNarrow("case360", "14.2.0");

        assertThat(narrowed.tenantId()).isEqualTo(tenantId);
        assertThat(narrowed.documentIds()).isEqualTo(docs);
        assertThat(narrowed.narrowProduct()).isEqualTo("case360");
        assertThat(narrowed.narrowVersion()).isEqualTo("14.2.0");
        assertThat(narrowed.hasVersionNarrow()).isTrue();
    }

    @Test
    void withVersionNarrow_partialNarrowStillCounts() {
        SearchScope base = new SearchScope(UUID.randomUUID(), Set.of(UUID.randomUUID()));
        assertThat(base.withVersionNarrow("case360", null).hasVersionNarrow()).isTrue();
        assertThat(base.withVersionNarrow(null, "14.2.0").hasVersionNarrow()).isTrue();
    }

    @Test
    void isEmpty_unaffectedByVersionNarrow() {
        SearchScope empty = new SearchScope(UUID.randomUUID(), Set.of());
        assertThat(empty.withVersionNarrow("case360", "14.2.0").isEmpty()).isTrue();
    }
}
