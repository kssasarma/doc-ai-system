package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.AnswerUpvote;

@Repository
public interface AnswerUpvoteRepository extends JpaRepository<AnswerUpvote, UUID> {
    long countByChatMessageId(UUID chatMessageId);
    boolean existsByChatMessageIdAndUserId(UUID chatMessageId, UUID userId);
    Optional<AnswerUpvote> findByChatMessageIdAndUserId(UUID chatMessageId, UUID userId);
    void deleteByChatMessageIdAndUserId(UUID chatMessageId, UUID userId);
    void deleteByUserId(UUID userId);
}
