package com.docai.bot.application.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.AnswerFeedback;
import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.repository.AnswerFeedbackRepository;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final AnswerFeedbackRepository feedbackRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    @Transactional
    public void submitFeedback(UUID messageId, UserPrincipal principal, int rating, String feedbackText) {
        if (rating != 1 && rating != -1) {
            throw new IllegalArgumentException("Rating must be 1 (helpful) or -1 (not helpful)");
        }

        ChatMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        ChatSession session = sessionRepository.findById(message.getChatId())
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        boolean isOwner = principal.userId().equals(session.getUserId());
        boolean isTenantAdmin = principal.isSuperAdmin()
            || (principal.isAdmin() && principal.tenantId() != null && principal.tenantId().equals(session.getTenantId()));
        if (!isOwner && !isTenantAdmin) {
            throw new AccessDeniedException("You do not have access to this chat message");
        }

        UUID userId = principal.userId();

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
                        .tenantId(session.getTenantId())
                        .rating((short) rating)
                        .feedbackText(feedbackText)
                        .build());
                    log.info("Saved feedback for message {} by user {}", messageId, userId);
                }
            );
    }
}
