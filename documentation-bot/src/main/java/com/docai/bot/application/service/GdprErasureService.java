package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.DigestProperties;
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
import com.docai.bot.domain.repository.SharedChatLinkRepository;
import com.docai.bot.domain.repository.SharedChatRecipientRepository;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.TopicSubscriptionRepository;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;
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
    private final DocumentAccessRepository documentAccessRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final AnswerFeedbackRepository feedbackRepository;
    private final AnswerUpvoteRepository upvoteRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SharedChatRecipientRepository sharedChatRecipientRepository;
    private final SharedChatLinkRepository sharedChatLinkRepository;
    private final AuditLogService auditLogService;
    private final JavaMailSender mailSender;
    private final DigestProperties digestProps;

    /** Self-injected (lazy, to avoid a circular construction) so {@link #processOne} calling
     * {@code self.eraseUser(...)} goes through the real Spring proxy — a direct {@code this.}
     * call would bypass {@code @Transactional} entirely (the same self-invocation footgun fixed
     * for AutoFaqService in Phase 5.8), meaning a failure partway through one request's erasure
     * would leave it partially applied with no rollback instead of atomically undoing it. */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private GdprErasureService self;

    @Value("${app.gdpr.deletion-grace-period-days:7}")
    private int gracePeriodDays;

    /**
     * Daily sweep of the {@code PENDING} queue {@link com.docai.bot.adapter.rest.GdprController#requestDeletion}
     * writes to — previously nothing ever consumed it, so a deletion request just sat there
     * forever unless an admin manually called {@link #eraseUser} some other way. The grace period
     * gives admins a window to notice/cancel an accidental or malicious request before it's
     * irreversible. Each request is erased in its own transaction so one failure doesn't roll
     * back — or block — every other pending request in the same run.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void processPendingDeletionRequests() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(gracePeriodDays);
        List<GdprDeletionRequest> due = deletionRequestRepository
            .findByStatusAndRequestedAtBefore("PENDING", cutoff);
        if (due.isEmpty()) return;

        log.info("GDPR deletion sweep: {} request(s) past the {}-day grace period", due.size(), gracePeriodDays);
        for (GdprDeletionRequest request : due) {
            try {
                processOne(request);
            } catch (Exception e) {
                log.error("GDPR deletion failed for request {} (user {}): {}",
                    request.getId(), request.getUserId(), e.getMessage(), e);
            }
        }
    }

    private void processOne(GdprDeletionRequest request) {
        self.eraseUser(request.getUserId());
        auditLogService.log(null, request.getTenantId(), "GDPR_ERASURE_COMPLETED", "USER", request.getUserId(),
            "deletionRequestId=" + request.getId(), null);
        notifyTenantAdmins(request.getTenantId(), request.getUserId());
    }

    private void notifyTenantAdmins(UUID tenantId, UUID erasedUserId) {
        try {
            for (User admin : userRepository.findByTenantIdAndRole(tenantId, User.Role.ADMIN)) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(digestProps.getFromAddress(), digestProps.getFromName());
                helper.setTo(admin.getEmail());
                helper.setSubject("A GDPR deletion request has been processed");
                helper.setText("A user's data erasure request (user id " + erasedUserId
                    + ") completed on " + LocalDateTime.now() + ".", false);
                mailSender.send(message);
            }
        } catch (Exception e) {
            // Never fail the erasure itself over a notification email — the erasure already
            // happened and is recorded (COMPLETED status + audit row); the admin can always check
            // the deletion-requests list instead.
            log.warn("Failed to notify tenant {} admins of GDPR erasure of user {}: {}",
                tenantId, erasedUserId, e.getMessage());
        }
    }

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
            sharedChatLinkRepository.deleteByChatId(session.getId());
        }
        sessionRepository.deleteByUserId(userId);

        // Revokes any share links this user created directly (belt-and-suspenders alongside the
        // per-session deleteByChatId loop above — both target the same rows via different keys).
        sharedChatLinkRepository.deleteByCreatedBy(userId);
        bookmarkRepository.deleteByUserId(userId);
        apiKeyRepository.deleteByUserId(userId);
        emailDigestRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        tenantMembershipRepository.deleteByUserId(userId);
        topicSubscriptionRepository.deleteByUserId(userId);
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
