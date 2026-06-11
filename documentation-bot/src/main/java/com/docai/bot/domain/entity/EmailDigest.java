package com.docai.bot.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "email_digests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Frequency frequency = Frequency.WEEKLY;

    @Column(name = "send_day")
    private Short sendDay;

    @Column(name = "send_hour", nullable = false)
    @Builder.Default
    private short sendHour = 8;

    @Column(name = "product_filter", length = 100)
    private String productFilter;

    @Column(name = "version_filter", length = 50)
    private String versionFilter;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "next_send_at")
    private Instant nextSendAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY
    }
}
