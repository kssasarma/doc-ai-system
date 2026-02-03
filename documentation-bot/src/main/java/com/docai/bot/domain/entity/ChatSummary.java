package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_summaries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSummary {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
