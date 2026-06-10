package com.docai.bot.config;

import java.util.UUID;

public record UserPrincipal(UUID userId, String username, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
