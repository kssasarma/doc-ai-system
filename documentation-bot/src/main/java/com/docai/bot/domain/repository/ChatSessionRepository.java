package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.ChatSession;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    
    List<ChatSession> findByLastActiveAtBefore(LocalDateTime dateTime);
}
