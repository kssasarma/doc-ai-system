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
import com.docai.bot.domain.entity.Escalation;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.EscalationRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRepository escalationRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final NotificationService notificationService;

    @Transactional
    public EscalationDTO createEscalation(UUID chatMessageId, String questionText, String aiAnswerText,
                                           String product, String version, UserPrincipal principal) {
        // Only one escalation per message
        if (escalationRepository.findByChatMessageId(chatMessageId).isPresent()) {
            throw new IllegalStateException("This message has already been escalated");
        }

        ChatSession session = sessionOwning(chatMessageId);
        assertSessionAccess(session, principal);

        Escalation e = Escalation.builder()
            .chatMessageId(chatMessageId)
            .tenantId(session.getTenantId())
            .questionText(questionText)
            .aiAnswerText(aiAnswerText)
            .product(product)
            .version(version)
            .createdBy(principal.userId())
            .build();
        e = escalationRepository.save(e);
        log.info("Created escalation {} for message {}", e.getId(), chatMessageId);
        return toDTO(e);
    }

    @Transactional(readOnly = true)
    public List<EscalationDTO> listEscalations(UserPrincipal principal) {
        List<Escalation> list = principal.isSuperAdmin()
            ? escalationRepository.findAll()
            : principal.isAdmin()
                ? escalationRepository.findByTenantIdOrderByCreatedAtDesc(principal.tenantId())
                : escalationRepository.findByCreatedByOrderByCreatedAtDesc(principal.userId());
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public EscalationDTO answerEscalation(UUID escalationId, UserPrincipal principal, String expertAnswer) {
        if (!principal.isAdmin() && !principal.isSuperAdmin()) {
            throw new AccessDeniedException("Only an admin can answer an escalation");
        }
        Escalation e = findInTenant(escalationId, principal);

        e.setExpertAnswer(expertAnswer);
        e.setAssignedTo(principal.userId());
        e.setStatus(Escalation.Status.ANSWERED);
        e.setAnsweredAt(LocalDateTime.now());
        e = escalationRepository.save(e);

        // Notify the question asker
        String expertUsername = userRepository.findById(principal.userId())
            .map(User::getUsername).orElse("an expert");
        notificationService.createNotification(
            e.getCreatedBy(),
            "ESCALATION_ANSWERED",
            "Your escalated question was answered",
            expertUsername + " answered: " + truncate(e.getQuestionText(), 100),
            e.getId()
        );

        log.info("Escalation {} answered by expert {}", escalationId, principal.userId());
        return toDTO(e);
    }

    @Transactional
    public EscalationDTO updateStatus(UUID escalationId, UserPrincipal principal, Escalation.Status newStatus) {
        if (!principal.isAdmin() && !principal.isSuperAdmin()) {
            throw new AccessDeniedException("Only an admin can update an escalation's status");
        }
        Escalation e = findInTenant(escalationId, principal);
        e.setStatus(newStatus);
        if (newStatus == Escalation.Status.IN_REVIEW) {
            e.setAssignedTo(principal.userId());
        }
        return toDTO(escalationRepository.save(e));
    }

    private Escalation findInTenant(UUID escalationId, UserPrincipal principal) {
        Escalation e = escalationRepository.findById(escalationId)
            .orElseThrow(() -> new IllegalArgumentException("Escalation not found"));
        if (!principal.isSuperAdmin() && !e.getTenantId().equals(principal.tenantId())) {
            throw new AccessDeniedException("You do not have access to this escalation");
        }
        return e;
    }

    private ChatSession sessionOwning(UUID chatMessageId) {
        ChatMessage message = messageRepository.findById(chatMessageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        return sessionRepository.findById(message.getChatId())
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private void assertSessionAccess(ChatSession session, UserPrincipal principal) {
        boolean isOwner = principal.userId().equals(session.getUserId());
        boolean isTenantAdmin = principal.isSuperAdmin()
            || (principal.isAdmin() && principal.tenantId() != null && principal.tenantId().equals(session.getTenantId()));
        if (!isOwner && !isTenantAdmin) {
            throw new AccessDeniedException("You do not have access to this chat message");
        }
    }

    private EscalationDTO toDTO(Escalation e) {
        String creatorUsername = userRepository.findById(e.getCreatedBy())
            .map(User::getUsername).orElse(null);
        String assignedUsername = e.getAssignedTo() != null
            ? userRepository.findById(e.getAssignedTo()).map(User::getUsername).orElse(null)
            : null;
        return EscalationDTO.builder()
            .id(e.getId().toString())
            .chatMessageId(e.getChatMessageId().toString())
            .questionText(e.getQuestionText())
            .aiAnswerText(e.getAiAnswerText())
            .status(e.getStatus().toString())
            .createdBy(e.getCreatedBy().toString())
            .createdByUsername(creatorUsername)
            .assignedTo(assignedUsername)
            .expertAnswer(e.getExpertAnswer())
            .product(e.getProduct())
            .version(e.getVersion())
            .createdAt(e.getCreatedAt())
            .answeredAt(e.getAnsweredAt())
            .build();
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }

    @lombok.Data @lombok.Builder
    public static class EscalationDTO {
        private String id;
        private String chatMessageId;
        private String questionText;
        private String aiAnswerText;
        private String status;
        private String createdBy;
        private String createdByUsername;
        private String assignedTo;
        private String expertAnswer;
        private String product;
        private String version;
        private LocalDateTime createdAt;
        private LocalDateTime answeredAt;
    }
}
