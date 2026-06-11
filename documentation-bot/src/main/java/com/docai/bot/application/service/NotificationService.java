package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Notification;
import com.docai.bot.domain.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void createNotification(UUID userId, String type, String title, String body, UUID referenceId) {
        notificationRepository.save(Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .body(body)
            .referenceId(referenceId)
            .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .filter(n -> !n.isRead())
            .collect(Collectors.toList());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
            .id(n.getId().toString())
            .type(n.getType())
            .title(n.getTitle())
            .body(n.getBody())
            .referenceId(n.getReferenceId() != null ? n.getReferenceId().toString() : null)
            .read(n.isRead())
            .createdAt(n.getCreatedAt())
            .build();
    }

    @lombok.Data @lombok.Builder
    public static class NotificationDTO {
        private String id;
        private String type;
        private String title;
        private String body;
        private String referenceId;
        private boolean read;
        private LocalDateTime createdAt;
    }
}
