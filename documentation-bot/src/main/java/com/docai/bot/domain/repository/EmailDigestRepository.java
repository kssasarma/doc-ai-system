package com.docai.bot.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.EmailDigest;

public interface EmailDigestRepository extends JpaRepository<EmailDigest, UUID> {
    Optional<EmailDigest> findByUserId(UUID userId);
    List<EmailDigest> findByEnabledTrueAndNextSendAtBefore(Instant threshold);
}
