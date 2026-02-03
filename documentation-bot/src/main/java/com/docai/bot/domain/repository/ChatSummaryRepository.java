package com.docai.bot.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.ChatSummary;

@Repository
public interface ChatSummaryRepository extends JpaRepository<ChatSummary, UUID> {
}
