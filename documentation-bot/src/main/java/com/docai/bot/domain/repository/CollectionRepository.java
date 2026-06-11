package com.docai.bot.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.Collection;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {
    List<Collection> findByCreatedByOrderByUpdatedAtDesc(UUID userId);
    List<Collection> findByPublicAccessTrueOrderByUpdatedAtDesc();
}
