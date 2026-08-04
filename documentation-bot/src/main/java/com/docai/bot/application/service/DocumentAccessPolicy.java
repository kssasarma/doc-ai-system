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

    /**
     * Resolves the eligibility gate for a chat scoped to one personal notebook — a completely
     * separate universe from {@link #resolveScope}, not a narrowing of it: a notebook's documents
     * are only ever searchable through this method, deliberately excluded from the tenant-wide
     * corpus (and any other user's, or even an ADMIN's, ordinary chat scope) so a private library
     * stays private. Returns an empty scope if {@code notebookId} isn't owned by {@code userId}
     * in this tenant, rather than throwing — callers treat "nothing to search" the same as any
     * other empty scope.
     */
    SearchScope resolveNotebookScope(UUID userId, UUID tenantId, UUID notebookId);
}
