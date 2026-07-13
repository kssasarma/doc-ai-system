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

/** A named-recipient grant on a {@link SharedChatLink} — lets the owner give specific same-tenant
 * users view access to a chat that's shared but not marked public, without making it visible to
 * the whole tenant. Mirrors {@link DocumentAccess}'s per-document grant model. */
@Entity
@Table(name = "shared_chat_recipients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedChatRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;
}
