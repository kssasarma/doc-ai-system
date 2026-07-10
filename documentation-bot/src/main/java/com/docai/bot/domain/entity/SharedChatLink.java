package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shared_chat_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedChatLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * Copied from the owning ChatSession's tenant at share-creation time. Nullable only for
     * links created before this column existed and whose session's own tenant was unresolvable
     * at backfill time — {@link com.docai.bot.application.service.SharedChatService} treats a
     * null tenantId on a non-public link as "deny unless owner/SUPER_ADMIN", never as a wildcard.
     */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "public_access", nullable = false)
    @Builder.Default
    private boolean publicAccess = false;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
