package com.docai.bot.config;

import java.util.UUID;

public record UserPrincipal(UUID userId, String username, String role, UUID tenantId, boolean mustChangePassword) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
}
