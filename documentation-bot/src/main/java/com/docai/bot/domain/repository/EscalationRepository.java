package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.Escalation;

@Repository
public interface EscalationRepository extends JpaRepository<Escalation, UUID> {
    List<Escalation> findByCreatedByOrderByCreatedAtDesc(UUID userId);
    List<Escalation> findAllByOrderByCreatedAtDesc();
    List<Escalation> findByStatusOrderByCreatedAtDesc(Escalation.Status status);
    Optional<Escalation> findByChatMessageId(UUID chatMessageId);
}
