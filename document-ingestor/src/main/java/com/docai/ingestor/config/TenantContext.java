package com.docai.ingestor.config;

import java.util.UUID;

/**
 * Thread-local holder for the current request's tenant identity.
 * Populated by JwtTokenFilter from the JWT "tenantId" claim and cleared at the end of the request.
 * There is no default/fallback tenant — {@link #get()} fails closed.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    /** Fails closed: throws if no tenant was resolved for this request. */
    public static UUID get() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new TenantNotResolvedException();
        }
        return id;
    }

    public static UUID getOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
