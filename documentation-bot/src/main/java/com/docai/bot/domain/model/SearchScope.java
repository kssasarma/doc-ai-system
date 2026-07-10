package com.docai.bot.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * The set of documents, within a single tenant, that a retrieval query is allowed to search.
 * This is the sole eligibility gate for chat retrieval (see {@link com.docai.bot.application.service.DocumentAccessPolicy}).
 *
 * {@code narrowProduct}/{@code narrowVersion} are an *optional*, opt-in narrowing on top of that
 * gate — set only when a caller explicitly pins a product/version (e.g. a chat request that
 * carries a user-selected scope chip, or {@code VersionDiffService} comparing two named
 * versions). Access (@code documentIds}) always wins: a narrow can only ever shrink the
 * accessible set further, never grant access to a document outside it.
 */
public record SearchScope(UUID tenantId, Set<UUID> documentIds, String narrowProduct, String narrowVersion) {

    public SearchScope(UUID tenantId, Set<UUID> documentIds) {
        this(tenantId, documentIds, null, null);
    }

    public boolean isEmpty() {
        return documentIds.isEmpty();
    }

    public boolean hasVersionNarrow() {
        return narrowProduct != null || narrowVersion != null;
    }

    public SearchScope withVersionNarrow(String product, String version) {
        return new SearchScope(tenantId, documentIds, product, version);
    }
}
