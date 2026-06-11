package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Collection;
import com.docai.bot.domain.entity.CollectionItem;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.CollectionItemRepository;
import com.docai.bot.domain.repository.CollectionRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionItemRepository itemRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public CollectionDTO createCollection(UUID userId, String name, String description, boolean publicAccess) {
        Collection c = Collection.builder()
            .name(name)
            .description(description)
            .createdBy(userId)
            .publicAccess(publicAccess)
            .build();
        return toDTO(collectionRepository.save(c), userId);
    }

    @Transactional(readOnly = true)
    public List<CollectionDTO> listCollections(UUID userId) {
        List<Collection> own = collectionRepository.findByCreatedByOrderByUpdatedAtDesc(userId);
        List<Collection> pub = collectionRepository.findByPublicAccessTrueOrderByUpdatedAtDesc()
            .stream()
            .filter(c -> !c.getCreatedBy().equals(userId))
            .collect(Collectors.toList());

        List<CollectionDTO> result = new ArrayList<>();
        own.forEach(c -> result.add(toDTO(c, userId)));
        pub.forEach(c -> result.add(toDTO(c, userId)));
        return result;
    }

    @Transactional
    public CollectionDTO updateCollection(UUID id, UUID userId, String name, String description, Boolean publicAccess) {
        Collection c = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
        if (!c.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Not authorized");
        }
        if (name != null) c.setName(name);
        if (description != null) c.setDescription(description);
        if (publicAccess != null) c.setPublicAccess(publicAccess);
        return toDTO(collectionRepository.save(c), userId);
    }

    @Transactional
    public void deleteCollection(UUID id, UUID userId) {
        Collection c = collectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
        if (!c.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Not authorized");
        }
        itemRepository.deleteByCollectionId(id);
        collectionRepository.delete(c);
        log.info("Deleted collection {} by user {}", id, userId);
    }

    @Transactional
    public CollectionItemDTO addItem(UUID collectionId, UUID chatMessageId, UUID chatId, String note, UUID userId) {
        Collection c = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new IllegalArgumentException("Collection not found"));

        if (!c.isPublicAccess() && !c.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Not authorized");
        }

        if (itemRepository.existsByCollectionIdAndChatMessageId(collectionId, chatMessageId)) {
            throw new IllegalStateException("Item already in collection");
        }

        int order = itemRepository.findByCollectionIdOrderByDisplayOrderAsc(collectionId).size();
        CollectionItem item = CollectionItem.builder()
            .collectionId(collectionId)
            .chatMessageId(chatMessageId)
            .chatId(chatId)
            .note(note)
            .addedBy(userId)
            .displayOrder(order)
            .build();
        item = itemRepository.save(item);

        // Touch updatedAt on parent collection
        collectionRepository.save(c);

        return toItemDTO(item, null);
    }

    @Transactional
    public void removeItem(UUID collectionId, UUID itemId, UUID userId) {
        CollectionItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        Collection c = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new IllegalArgumentException("Collection not found"));

        if (!item.getCollectionId().equals(collectionId)) {
            throw new IllegalArgumentException("Item does not belong to collection");
        }
        if (!c.getCreatedBy().equals(userId) && !item.getAddedBy().equals(userId)) {
            throw new IllegalStateException("Not authorized");
        }
        itemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public List<CollectionItemDTO> getItems(UUID collectionId, UUID userId) {
        Collection c = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
        if (!c.isPublicAccess() && !c.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Not authorized");
        }

        return itemRepository.findByCollectionIdOrderByDisplayOrderAsc(collectionId)
            .stream()
            .map(item -> {
                String content = messageRepository.findById(item.getChatMessageId())
                    .map(m -> m.getContent().length() > 500
                        ? m.getContent().substring(0, 500) + "…"
                        : m.getContent())
                    .orElse(null);
                String addedByUsername = userRepository.findById(item.getAddedBy())
                    .map(User::getUsername).orElse(null);
                return toItemDTO(item, content, addedByUsername);
            })
            .collect(Collectors.toList());
    }

    private CollectionDTO toDTO(Collection c, UUID requestingUserId) {
        String creatorUsername = userRepository.findById(c.getCreatedBy())
            .map(User::getUsername).orElse(null);
        long itemCount = itemRepository.findByCollectionIdOrderByDisplayOrderAsc(c.getId()).size();
        return CollectionDTO.builder()
            .id(c.getId().toString())
            .name(c.getName())
            .description(c.getDescription())
            .publicAccess(c.isPublicAccess())
            .createdBy(c.getCreatedBy().toString())
            .createdByUsername(creatorUsername)
            .isOwner(c.getCreatedBy().equals(requestingUserId))
            .itemCount((int) itemCount)
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }

    private CollectionItemDTO toItemDTO(CollectionItem item, String content) {
        return toItemDTO(item, content, null);
    }

    private CollectionItemDTO toItemDTO(CollectionItem item, String content, String addedByUsername) {
        return CollectionItemDTO.builder()
            .id(item.getId().toString())
            .collectionId(item.getCollectionId().toString())
            .chatMessageId(item.getChatMessageId().toString())
            .chatId(item.getChatId().toString())
            .messageContent(content)
            .note(item.getNote())
            .addedByUsername(addedByUsername)
            .createdAt(item.getCreatedAt())
            .build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @lombok.Data @lombok.Builder
    public static class CollectionDTO {
        private String id;
        private String name;
        private String description;
        private boolean publicAccess;
        private String createdBy;
        private String createdByUsername;
        private boolean isOwner;
        private int itemCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @lombok.Data @lombok.Builder
    public static class CollectionItemDTO {
        private String id;
        private String collectionId;
        private String chatMessageId;
        private String chatId;
        private String messageContent;
        private String note;
        private String addedByUsername;
        private LocalDateTime createdAt;
    }
}
