package com.docai.bot.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.AnswerUpvote;
import com.docai.bot.domain.repository.AnswerUpvoteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerUpvoteService {

    private final AnswerUpvoteRepository upvoteRepository;

    @Transactional
    public UpvoteResult toggleUpvote(UUID messageId, UUID userId) {
        if (upvoteRepository.existsByChatMessageIdAndUserId(messageId, userId)) {
            upvoteRepository.deleteByChatMessageIdAndUserId(messageId, userId);
        } else {
            upvoteRepository.save(AnswerUpvote.builder()
                .chatMessageId(messageId)
                .userId(userId)
                .build());
        }
        long count = upvoteRepository.countByChatMessageId(messageId);
        boolean upvoted = upvoteRepository.existsByChatMessageIdAndUserId(messageId, userId);
        return new UpvoteResult(messageId.toString(), count, upvoted);
    }

    @Transactional(readOnly = true)
    public UpvoteResult getStatus(UUID messageId, UUID userId) {
        long count = upvoteRepository.countByChatMessageId(messageId);
        boolean upvoted = upvoteRepository.existsByChatMessageIdAndUserId(messageId, userId);
        return new UpvoteResult(messageId.toString(), count, upvoted);
    }

    public record UpvoteResult(String chatMessageId, long upvoteCount, boolean userUpvoted) {}
}
