package com.docai.bot.config;

import java.util.UUID;

/**
 * Thread-local holder for the current request's tenant identity.
 * Populated by TenantResolutionFilter at the start of every request and cleared at the end.
 */
public final class TenantContext {

    private static final UUID DEFAULT_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        UUID id = CURRENT.get();
        return id != null ? id : DEFAULT_TENANT_ID;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
