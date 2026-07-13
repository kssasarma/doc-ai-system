package com.docai.bot.application.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.AnswerUpvote;
import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.repository.AnswerUpvoteRepository;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerUpvoteService {

    private final AnswerUpvoteRepository upvoteRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    @Transactional
    public UpvoteResult toggleUpvote(UUID messageId, UserPrincipal principal) {
        ChatSession session = assertMessageAccess(messageId, principal);
        UUID userId = principal.userId();

        if (upvoteRepository.existsByChatMessageIdAndUserId(messageId, userId)) {
            upvoteRepository.deleteByChatMessageIdAndUserId(messageId, userId);
        } else {
            upvoteRepository.save(AnswerUpvote.builder()
                .chatMessageId(messageId)
                .userId(userId)
                .tenantId(session.getTenantId())
                .build());
        }
        long count = upvoteRepository.countByChatMessageId(messageId);
        boolean upvoted = upvoteRepository.existsByChatMessageIdAndUserId(messageId, userId);
        return new UpvoteResult(messageId.toString(), count, upvoted);
    }

    @Transactional(readOnly = true)
    public UpvoteResult getStatus(UUID messageId, UserPrincipal principal) {
        assertMessageAccess(messageId, principal);
        UUID userId = principal.userId();
        long count = upvoteRepository.countByChatMessageId(messageId);
        boolean upvoted = upvoteRepository.existsByChatMessageIdAndUserId(messageId, userId);
        return new UpvoteResult(messageId.toString(), count, upvoted);
    }

    private ChatSession assertMessageAccess(UUID messageId, UserPrincipal principal) {
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
        return session;
    }

    public record UpvoteResult(String chatMessageId, long upvoteCount, boolean userUpvoted) {}
}
