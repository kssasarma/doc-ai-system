package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.AnswerFeedback;

@Repository
public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, UUID> {

    Optional<AnswerFeedback> findByChatMessageIdAndUserId(UUID chatMessageId, UUID userId);

    long countByTenantIdAndRating(UUID tenantId, short rating);
    void deleteByUserId(UUID userId);
}
