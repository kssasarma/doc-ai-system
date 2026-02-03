package com.docai.bot.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.ChatMessage;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    
    List<ChatMessage> findByChatIdOrderByCreatedAtDesc(UUID chatId);
    
    @Query(value = "SELECT * FROM chat_messages WHERE chat_id = :chatId " +
                   "ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<ChatMessage> findRecentMessages(UUID chatId, int limit);
    
    long countByChatId(UUID chatId);
}
