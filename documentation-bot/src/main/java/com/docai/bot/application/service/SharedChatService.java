package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.entity.SharedChatLink;
import com.docai.bot.domain.entity.SharedChatRecipient;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.SharedChatLinkRepository;
import com.docai.bot.domain.repository.SharedChatRecipientRepository;
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
    private final SharedChatRecipientRepository recipientRepository;

    @Transactional
    public ShareLinkDTO createShareLink(UUID chatId, UserPrincipal creator, boolean publicAccess, Integer expireDays) {
        ChatSession session = sessionRepository.findById(chatId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(creator.userId())) {
            throw new AccessDeniedException("You do not own this chat session");
        }

        // Upsert: replace any existing link for this session
        linkRepository.findByChatId(chatId).ifPresent(existing -> linkRepository.delete(existing));

        LocalDateTime expiresAt = expireDays != null
            ? LocalDateTime.now().plusDays(expireDays)
            : null;

        SharedChatLink link = SharedChatLink.builder()
            .chatId(chatId)
            .token(UUID.randomUUID().toString())
            .createdBy(creator.userId())
            .tenantId(session.getTenantId())
            .publicAccess(publicAccess)
            .expiresAt(expiresAt)
            .build();
        link = linkRepository.save(link);
        log.info("Created share link {} for session {}", link.getToken(), chatId);
        return toDTO(link);
    }

    /**
     * Updates an existing link's visibility/expiration in place, preserving its token — unlike
     * {@link #createShareLink}, which mints a fresh token. Editing settings must not invalidate a
     * URL a creator has already handed out, so this never touches the token.
     */
    @Transactional
    public ShareLinkDTO updateShareLink(UUID chatId, UserPrincipal editor, boolean publicAccess, Integer expireDays) {
        SharedChatLink link = linkRepository.findByChatId(chatId)
            .orElseThrow(() -> new IllegalArgumentException("No share link exists for this session"));
        if (!link.getCreatedBy().equals(editor.userId())) {
            throw new AccessDeniedException("You do not own this share link");
        }

        link.setPublicAccess(publicAccess);
        link.setExpiresAt(expireDays != null ? LocalDateTime.now().plusDays(expireDays) : null);
        link = linkRepository.save(link);
        log.info("Updated share link {} for session {}", link.getToken(), chatId);
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

    /**
     * Grants a specific same-tenant user view access to a non-public share link. Once a link has
     * at least one named recipient, {@link #verifyViewAccess} stops falling back to "anyone in
     * the tenant" for it and restricts viewing to exactly the owner, SUPER_ADMIN, and the
     * recipients explicitly listed here — mirrors the per-document access grant model.
     */
    @Transactional
    public RecipientDTO addRecipient(UUID chatId, UserPrincipal owner, UUID recipientUserId) {
        SharedChatLink link = ownedLink(chatId, owner.userId());

        User recipient = userRepository.findById(recipientUserId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (link.getTenantId() == null || !link.getTenantId().equals(recipient.getTenantId())) {
            throw new IllegalArgumentException("User must be in the same tenant as this chat");
        }
        if (recipientRepository.existsByLinkIdAndUserId(link.getId(), recipientUserId)) {
            return toRecipientDTO(recipientRepository.findByLinkIdAndUserId(link.getId(), recipientUserId).get(), recipient);
        }

        SharedChatRecipient saved = recipientRepository.save(SharedChatRecipient.builder()
            .linkId(link.getId())
            .userId(recipientUserId)
            .grantedBy(owner.userId())
            .build());
        log.info("Granted user {} access to shared chat {}", recipientUserId, chatId);
        return toRecipientDTO(saved, recipient);
    }

    @Transactional
    public void removeRecipient(UUID chatId, UserPrincipal owner, UUID recipientUserId) {
        SharedChatLink link = ownedLink(chatId, owner.userId());
        recipientRepository.deleteByLinkIdAndUserId(link.getId(), recipientUserId);
        log.info("Revoked user {}'s access to shared chat {}", recipientUserId, chatId);
    }

    @Transactional(readOnly = true)
    public List<RecipientDTO> listRecipients(UUID chatId, UserPrincipal owner) {
        SharedChatLink link = ownedLink(chatId, owner.userId());
        return recipientRepository.findByLinkId(link.getId()).stream()
            .map(r -> toRecipientDTO(r, userRepository.findById(r.getUserId()).orElse(null)))
            .collect(Collectors.toList());
    }

    private SharedChatLink ownedLink(UUID chatId, UUID ownerId) {
        SharedChatLink link = linkRepository.findByChatId(chatId)
            .orElseThrow(() -> new IllegalArgumentException("No share link exists for this session"));
        if (!link.getCreatedBy().equals(ownerId)) {
            throw new AccessDeniedException("You do not own this share link");
        }
        return link;
    }

    @Transactional(readOnly = true)
    public SharedChatViewDTO getSharedChat(String token, UserPrincipal viewer) {
        SharedChatLink link = linkRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Share link not found"));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Share link has expired");
        }

        verifyViewAccess(link, viewer);

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
    public String forkSharedChat(String token, UserPrincipal forker) {
        SharedChatLink link = linkRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Share link not found"));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Share link has expired");
        }

        verifyViewAccess(link, forker);

        ChatSession original = sessionRepository.findById(link.getChatId())
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ChatSession forked = ChatSession.builder()
            .userId(forker.userId())
            .tenantId(forker.tenantId())
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

    /**
     * A public link is viewable by anyone. A non-public link is viewable by its creator or a
     * SUPER_ADMIN always; beyond that, it falls into one of two tiers depending on whether the
     * owner has granted any named recipients (see {@link #addRecipient}):
     * <ul>
     *   <li>No recipients configured — visible to any signed-in member of the chat's tenant (the
     *       original "workspace" behavior, preserved so every pre-existing link keeps working
     *       exactly as before).
     *   <li>One or more recipients configured — visible <em>only</em> to those specific users,
     *       not the whole tenant; adding the first recipient narrows an already-shared link from
     *       workspace-wide down to named individuals.
     * </ul>
     * Never viewable by an anonymous caller either way. A null {@code link.tenantId}
     * (pre-migration data whose owning session's own tenant was unresolvable) denies rather than
     * wildcards: it falls through to "owner or SUPER_ADMIN only", the safe default for this
     * codebase's fail-closed tenant model.
     */
    private void verifyViewAccess(SharedChatLink link, UserPrincipal viewer) {
        if (link.isPublicAccess()) {
            return;
        }
        if (viewer == null) {
            throw new AccessDeniedException("Sign in to view this shared chat");
        }
        boolean isOwner = viewer.userId().equals(link.getCreatedBy());
        if (isOwner || viewer.isSuperAdmin()) {
            return;
        }

        List<SharedChatRecipient> recipients = recipientRepository.findByLinkId(link.getId());
        boolean allowed;
        if (!recipients.isEmpty()) {
            allowed = recipients.stream().anyMatch(r -> r.getUserId().equals(viewer.userId()));
        } else {
            allowed = link.getTenantId() != null && link.getTenantId().equals(viewer.tenantId());
        }
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this shared chat");
        }
    }

    private ShareLinkDTO toDTO(SharedChatLink link) {
        return ShareLinkDTO.builder()
            .token(link.getToken())
            .chatId(link.getChatId().toString())
            .publicAccess(link.isPublicAccess())
            .expiresAt(link.getExpiresAt())
            .createdAt(link.getCreatedAt())
            .recipientCount(recipientRepository.findByLinkId(link.getId()).size())
            .build();
    }

    private RecipientDTO toRecipientDTO(SharedChatRecipient recipient, User user) {
        return RecipientDTO.builder()
            .userId(recipient.getUserId().toString())
            .username(user != null ? user.getUsername() : "unknown")
            .grantedAt(recipient.getGrantedAt())
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
        /** How many named recipients this link has — 0 means "workspace-visible" (the default),
         * >0 means visibility is narrowed to exactly those people (see verifyViewAccess). */
        private int recipientCount;
    }

    @lombok.Data @lombok.Builder
    public static class RecipientDTO {
        private String userId;
        private String username;
        private LocalDateTime grantedAt;
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
