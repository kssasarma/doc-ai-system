package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.entity.SharedChatLink;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.SharedChatLinkRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedChatService {

    private final SharedChatLinkRepository linkRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ShareLinkDTO createShareLink(UUID chatId, UUID userId, boolean publicAccess, Integer expireDays) {
        // Upsert: replace any existing link for this session
        linkRepository.findByChatId(chatId).ifPresent(existing -> linkRepository.delete(existing));

        LocalDateTime expiresAt = expireDays != null
            ? LocalDateTime.now().plusDays(expireDays)
            : null;

        SharedChatLink link = SharedChatLink.builder()
            .chatId(chatId)
            .token(UUID.randomUUID().toString())
            .createdBy(userId)
            .publicAccess(publicAccess)
            .expiresAt(expiresAt)
            .build();
        link = linkRepository.save(link);
        log.info("Created share link {} for session {}", link.getToken(), chatId);
        return toDTO(link);
    }

    @Transactional(readOnly = true)
    public ShareLinkDTO getShareLink(UUID chatId, UUID userId) {
        return linkRepository.findByChatId(chatId)
            .filter(link -> link.getCreatedBy().equals(userId))
            .map(this::toDTO)
            .orElse(null);
    }

    @Transactional
    public void deleteShareLink(UUID chatId, UUID userId) {
        linkRepository.findByChatId(chatId).ifPresent(link -> {
            if (link.getCreatedBy().equals(userId)) {
                linkRepository.delete(link);
                log.info("Deleted share link for session {}", chatId);
            }
        });
    }

    @Transactional(readOnly = true)
    public SharedChatViewDTO getSharedChat(String token) {
        SharedChatLink link = linkRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Share link not found"));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Share link has expired");
        }

        ChatSession session = sessionRepository.findById(link.getChatId())
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        List<ChatMessage> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(link.getChatId());

        String creatorUsername = userRepository.findById(link.getCreatedBy())
            .map(User::getUsername).orElse("unknown");

        return SharedChatViewDTO.builder()
            .token(token)
            .chatId(session.getId().toString())
            .title(session.getTitle())
            .product(session.getProduct())
            .version(session.getVersion())
            .createdByUsername(creatorUsername)
            .expiresAt(link.getExpiresAt())
            .messages(messages.stream().map(m -> SharedMessageDTO.builder()
                .role(m.getRole().toString())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build()).collect(Collectors.toList()))
            .build();
    }

    @Transactional
    public String forkSharedChat(String token, UUID targetUserId) {
        SharedChatLink link = linkRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Share link not found"));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Share link has expired");
        }

        ChatSession original = sessionRepository.findById(link.getChatId())
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ChatSession forked = ChatSession.builder()
            .userId(targetUserId)
            .product(original.getProduct())
            .version(original.getVersion())
            .title((original.getTitle() != null ? original.getTitle() : "Forked chat"))
            .messageCount(original.getMessageCount())
            .build();
        forked = sessionRepository.save(forked);

        final UUID forkedId = forked.getId();
        List<ChatMessage> originalMessages = messageRepository.findByChatIdOrderByCreatedAtAsc(link.getChatId());
        originalMessages.forEach(m -> messageRepository.save(ChatMessage.builder()
            .chatId(forkedId)
            .role(m.getRole())
            .content(m.getContent())
            .build()));

        log.info("Forked shared chat {} into new session {}", token, forkedId);
        return forkedId.toString();
    }

    private ShareLinkDTO toDTO(SharedChatLink link) {
        return ShareLinkDTO.builder()
            .token(link.getToken())
            .chatId(link.getChatId().toString())
            .publicAccess(link.isPublicAccess())
            .expiresAt(link.getExpiresAt())
            .createdAt(link.getCreatedAt())
            .build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @lombok.Data @lombok.Builder
    public static class ShareLinkDTO {
        private String token;
        private String chatId;
        private boolean publicAccess;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }

    @lombok.Data @lombok.Builder
    public static class SharedChatViewDTO {
        private String token;
        private String chatId;
        private String title;
        private String product;
        private String version;
        private String createdByUsername;
        private LocalDateTime expiresAt;
        private List<SharedMessageDTO> messages;
    }

    @lombok.Data @lombok.Builder
    public static class SharedMessageDTO {
        private String role;
        private String content;
        private LocalDateTime createdAt;
    }
}
