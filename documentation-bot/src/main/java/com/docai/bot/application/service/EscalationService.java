package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Escalation;
import com.docai.bot.domain.entity.User;
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
    private final NotificationService notificationService;

    @Transactional
    public EscalationDTO createEscalation(UUID chatMessageId, String questionText, String aiAnswerText,
                                           String product, String version, UUID userId) {
        // Only one escalation per message
        if (escalationRepository.findByChatMessageId(chatMessageId).isPresent()) {
            throw new IllegalStateException("This message has already been escalated");
        }

        Escalation e = Escalation.builder()
            .chatMessageId(chatMessageId)
            .questionText(questionText)
            .aiAnswerText(aiAnswerText)
            .product(product)
            .version(version)
            .createdBy(userId)
            .build();
        e = escalationRepository.save(e);
        log.info("Created escalation {} for message {}", e.getId(), chatMessageId);
        return toDTO(e);
    }

    @Transactional(readOnly = true)
    public List<EscalationDTO> listEscalations(UUID userId, boolean isAdmin) {
        List<Escalation> list = isAdmin
            ? escalationRepository.findAllByOrderByCreatedAtDesc()
            : escalationRepository.findByCreatedByOrderByCreatedAtDesc(userId);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public EscalationDTO answerEscalation(UUID escalationId, UUID expertId, String expertAnswer) {
        Escalation e = escalationRepository.findById(escalationId)
            .orElseThrow(() -> new IllegalArgumentException("Escalation not found"));

        e.setExpertAnswer(expertAnswer);
        e.setAssignedTo(expertId);
        e.setStatus(Escalation.Status.ANSWERED);
        e.setAnsweredAt(LocalDateTime.now());
        e = escalationRepository.save(e);

        // Notify the question asker
        String expertUsername = userRepository.findById(expertId)
            .map(User::getUsername).orElse("an expert");
        notificationService.createNotification(
            e.getCreatedBy(),
            "ESCALATION_ANSWERED",
            "Your escalated question was answered",
            expertUsername + " answered: " + truncate(e.getQuestionText(), 100),
            e.getId()
        );

        log.info("Escalation {} answered by expert {}", escalationId, expertId);
        return toDTO(e);
    }

    @Transactional
    public EscalationDTO updateStatus(UUID escalationId, UUID userId, Escalation.Status newStatus) {
        Escalation e = escalationRepository.findById(escalationId)
            .orElseThrow(() -> new IllegalArgumentException("Escalation not found"));
        e.setStatus(newStatus);
        if (newStatus == Escalation.Status.IN_REVIEW) {
            e.setAssignedTo(userId);
        }
        return toDTO(escalationRepository.save(e));
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
