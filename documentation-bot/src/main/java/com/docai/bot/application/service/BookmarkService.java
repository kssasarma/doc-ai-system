package com.docai.bot.application.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Bookmark;
import com.docai.bot.domain.repository.BookmarkRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;

    @Transactional
    public BookmarkDTO createBookmark(UUID userId, UUID chatMessageId, UUID chatId,
                                      String messageExcerpt, String title, String note,
                                      String[] tags) {
        Bookmark bookmark = Bookmark.builder()
            .userId(userId)
            .chatMessageId(chatMessageId)
            .chatId(chatId)
            .messageExcerpt(messageExcerpt)
            .title(title)
            .note(note)
            .tags(tags != null ? tags : new String[0])
            .build();
        Bookmark saved = bookmarkRepository.save(bookmark);
        log.info("Created bookmark {} for user {}", saved.getId(), userId);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<BookmarkDTO> getBookmarks(UUID userId) {
        return bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteBookmark(UUID bookmarkId, UUID userId) {
        bookmarkRepository.findByIdAndUserId(bookmarkId, userId)
            .ifPresent(b -> {
                bookmarkRepository.delete(b);
                log.info("Deleted bookmark {} for user {}", bookmarkId, userId);
            });
    }

    @Transactional
    public BookmarkDTO updateBookmark(UUID bookmarkId, UUID userId, String note, String[] tags) {
        Bookmark bookmark = bookmarkRepository.findByIdAndUserId(bookmarkId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Bookmark not found"));
        if (note != null) bookmark.setNote(note);
        if (tags != null) bookmark.setTags(tags);
        return toDTO(bookmarkRepository.save(bookmark));
    }

    public boolean isBookmarked(UUID userId, UUID chatMessageId) {
        return bookmarkRepository.existsByUserIdAndChatMessageId(userId, chatMessageId);
    }

    private BookmarkDTO toDTO(Bookmark b) {
        return BookmarkDTO.builder()
            .id(b.getId().toString())
            .chatMessageId(b.getChatMessageId().toString())
            .chatId(b.getChatId().toString())
            .messageExcerpt(b.getMessageExcerpt())
            .title(b.getTitle())
            .note(b.getNote())
            .tags(b.getTags())
            .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class BookmarkDTO {
        private String id;
        private String chatMessageId;
        private String chatId;
        private String messageExcerpt;
        private String title;
        private String note;
        private String[] tags;
        private String createdAt;
    }
}
