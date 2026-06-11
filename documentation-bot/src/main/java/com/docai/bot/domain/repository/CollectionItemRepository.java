package com.docai.bot.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.CollectionItem;

@Repository
public interface CollectionItemRepository extends JpaRepository<CollectionItem, UUID> {
    List<CollectionItem> findByCollectionIdOrderByDisplayOrderAsc(UUID collectionId);
    boolean existsByCollectionIdAndChatMessageId(UUID collectionId, UUID chatMessageId);
    void deleteByCollectionId(UUID collectionId);
}
