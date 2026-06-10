package com.docai.bot.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.AnswerFeedback;
import com.docai.bot.domain.repository.AnswerFeedbackRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final AnswerFeedbackRepository feedbackRepository;

    @Transactional
    public void submitFeedback(UUID messageId, UUID userId, int rating, String feedbackText) {
        if (rating != 1 && rating != -1) {
            throw new IllegalArgumentException("Rating must be 1 (helpful) or -1 (not helpful)");
        }

        // Upsert: replace any previous rating from this user on this message
        feedbackRepository.findByChatMessageIdAndUserId(messageId, userId)
            .ifPresentOrElse(
                existing -> {
                    existing.setRating((short) rating);
                    existing.setFeedbackText(feedbackText);
                    feedbackRepository.save(existing);
                    log.info("Updated feedback for message {} by user {}", messageId, userId);
                },
                () -> {
                    feedbackRepository.save(AnswerFeedback.builder()
                        .chatMessageId(messageId)
                        .userId(userId)
                        .rating((short) rating)
                        .feedbackText(feedbackText)
                        .build());
                    log.info("Saved feedback for message {} by user {}", messageId, userId);
                }
            );
    }
}
