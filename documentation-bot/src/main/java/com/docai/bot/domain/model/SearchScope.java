package com.docai.bot.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * The set of documents, within a single tenant, that a retrieval query is allowed to search.
 * This is the sole eligibility gate for chat retrieval (see {@link com.docai.bot.application.service.DocumentAccessPolicy}) —
 * product/version are descriptive metadata only and never narrow this set.
 */
public record SearchScope(UUID tenantId, Set<UUID> documentIds) {

    public boolean isEmpty() {
        return documentIds.isEmpty();
    }
}
