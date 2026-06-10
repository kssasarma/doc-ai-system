package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookmarks", indexes = {
    @Index(name = "idx_bookmark_user", columnList = "user_id"),
    @Index(name = "idx_bookmark_message", columnList = "chat_message_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "chat_message_id", nullable = false)
    private UUID chatMessageId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "message_excerpt", columnDefinition = "TEXT")
    private String messageExcerpt;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
