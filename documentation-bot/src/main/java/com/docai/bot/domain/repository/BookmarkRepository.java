package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.Bookmark;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, UUID> {
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Bookmark> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndChatMessageId(UUID userId, UUID chatMessageId);
}
