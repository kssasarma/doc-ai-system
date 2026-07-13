package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.entity.GdprDeletionRequest;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.AnswerFeedbackRepository;
import com.docai.bot.domain.repository.AnswerUpvoteRepository;
import com.docai.bot.domain.repository.ApiKeyRepository;
import com.docai.bot.domain.repository.BookmarkRepository;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.ChatSummaryRepository;
import com.docai.bot.domain.repository.DocumentAccessRepository;
import com.docai.bot.domain.repository.EmailDigestRepository;
import com.docai.bot.domain.repository.GdprDeletionRequestRepository;
import com.docai.bot.domain.repository.GroupMembershipRepository;
import com.docai.bot.domain.repository.NotificationRepository;
import com.docai.bot.domain.repository.RefreshTokenRepository;
import com.docai.bot.domain.repository.SharedChatRecipientRepository;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.TopicSubscriptionRepository;
import com.docai.bot.domain.repository.UserProductAccessRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GDPR Article 17 — Right to Erasure. Processes a {@link GdprDeletionRequest}: deletes the
 * user's own private data outright, then anonymizes (rather than deletes) the {@code User} row
 * itself.
 *
 * <p>The row is kept on purpose. A large set of collaborative tables — collections, groups,
 * escalations, chunk annotations, FAQ approvals, invitations, document/group access grants —
 * hold a {@code NOT NULL} foreign key to {@code users(id)} with no {@code ON DELETE} clause
 * (defaults to RESTRICT), because deleting a contributor should not silently destroy content
 * their teammates still rely on. Anonymizing the row satisfies erasure (no PII survives) without
 * that collateral damage or a large cascade-rewrite migration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprErasureService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GdprDeletionRequestRepository deletionRequestRepository;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final EmailDigestRepository emailDigestRepository;
    private final NotificationRepository notificationRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TopicSubscriptionRepository topicSubscriptionRepository;
    private final UserProductAccessRepository userProductAccessRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final AnswerFeedbackRepository feedbackRepository;
    private final AnswerUpvoteRepository upvoteRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SharedChatRecipientRepository sharedChatRecipientRepository;

    @Transactional
    public void eraseUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // chat_messages/chat_summaries have no DB-level FK to chat_sessions (app-managed, same
        // as ChatService#deleteChatSession) so each session's children must be deleted explicitly.
        List<ChatSession> sessions = sessionRepository.findByUserIdOrderByLastActiveAtDesc(userId);
        for (ChatSession session : sessions) {
            messageRepository.deleteByChatId(session.getId());
            summaryRepository.deleteById(session.getId());
        }
        sessionRepository.deleteByUserId(userId);

        bookmarkRepository.deleteByUserId(userId);
        apiKeyRepository.deleteByUserId(userId);
        emailDigestRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        tenantMembershipRepository.deleteByUserId(userId);
        topicSubscriptionRepository.deleteByUserId(userId);
        userProductAccessRepository.deleteByUserId(userId);
        documentAccessRepository.deleteByUserId(userId);
        groupMembershipRepository.deleteByUserId(userId);
        feedbackRepository.deleteByUserId(userId);
        upvoteRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        sharedChatRecipientRepository.deleteByUserId(userId);

        String tombstone = "deleted-" + userId;
        user.setUsername(tombstone);
        user.setEmail(tombstone + "@erased.invalid");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDisplayName(null);
        user.setAvatarUrl(null);
        user.setOidcSub(null);
        user.setOidcProvider(null);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        LocalDateTime now = LocalDateTime.now();
        for (GdprDeletionRequest request : deletionRequestRepository.findByUserId(userId)) {
            if (!"COMPLETED".equals(request.getStatus())) {
                request.setStatus("COMPLETED");
                request.setProcessedAt(now);
                deletionRequestRepository.save(request);
            }
        }

        log.info("Erased user {} (GDPR Article 17)", userId);
    }
}
