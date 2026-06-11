package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "topic_subscriptions", indexes = {
    @Index(name = "idx_topic_subs_user",    columnList = "user_id"),
    @Index(name = "idx_topic_subs_product", columnList = "product,version")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "topic", columnDefinition = "TEXT", nullable = false)
    private String topic;

    @Column(name = "product", length = 100)
    private String product;

    @Column(name = "version", length = 50)
    private String version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
