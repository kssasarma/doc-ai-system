package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "escalations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_message_id", nullable = false)
    private UUID chatMessageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "ai_answer_text", columnDefinition = "TEXT")
    private String aiAnswerText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "expert_answer", columnDefinition = "TEXT")
    private String expertAnswer;

    @Column(length = 100)
    private String product;

    @Column(length = 50)
    private String version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    public enum Status { PENDING, IN_REVIEW, ANSWERED, CLOSED }
}
