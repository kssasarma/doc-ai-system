package com.docai.bot.application.service;

import java.util.UUID;

import com.docai.bot.domain.model.SearchScope;

/**
 * Resolves which documents a user may search within their tenant. This is the single seam
 * retrieval code depends on (see {@link VectorSearchService}) — swapping or layering how
 * eligibility is decided (e.g. team-based access, a bulk "everything under product X" grant)
 * means adding or replacing an implementation of this interface, never touching the callers.
 */
public interface DocumentAccessPolicy {

    SearchScope resolveScope(UUID userId, UUID tenantId);
}
