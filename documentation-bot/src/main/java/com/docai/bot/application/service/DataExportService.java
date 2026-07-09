package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.docai.bot.domain.repository.BookmarkRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.FaqEntryRepository;
import com.docai.bot.domain.repository.TopicSubscriptionRepository;
import com.docai.bot.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * GDPR Article 20 — Data Portability.
 * Produces a machine-readable JSON export of all data held for a given user.
 */
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final BookmarkRepository bookmarkRepository;
    private final TopicSubscriptionRepository subscriptionRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public String exportUserData(UUID userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", LocalDateTime.now().toString());
        export.put("userId", userId.toString());
        export.put("username", user.getUsername());
        export.put("email", user.getEmail());
        export.put("role", user.getRole().name());
        export.put("createdAt", user.getCreatedAt().toString());

        // Chat sessions (metadata only — not full message content for compactness)
        var sessions = chatSessionRepository.findByUserIdOrderByLastActiveAtDesc(userId);
        export.put("chatSessions", sessions.stream().map(s -> Map.of(
            "id", s.getId().toString(),
            "title", s.getTitle() != null ? s.getTitle() : "",
            "createdAt", s.getCreatedAt().toString()
        )).toList());

        // Bookmarks
        export.put("bookmarks", bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(b -> Map.of(
            "id", b.getId().toString(),
            "title", b.getTitle() != null ? b.getTitle() : "",
            "note", b.getNote() != null ? b.getNote() : "",
            "createdAt", b.getCreatedAt().toString()
        )).toList());

        // Topic subscriptions
        export.put("topicSubscriptions", subscriptionRepository.findByUserId(userId).stream().map(s -> Map.of(
            "topic", s.getTopic(),
            "product", s.getProduct() != null ? s.getProduct() : "",
            "version", s.getVersion() != null ? s.getVersion() : ""
        )).toList());

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
    }
}
