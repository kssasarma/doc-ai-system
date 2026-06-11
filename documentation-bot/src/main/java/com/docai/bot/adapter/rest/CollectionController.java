package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.CollectionService;
import com.docai.bot.application.service.CollectionService.CollectionDTO;
import com.docai.bot.application.service.CollectionService.CollectionItemDTO;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public ResponseEntity<List<CollectionDTO>> listCollections(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(collectionService.listCollections(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<CollectionDTO> createCollection(
            @Valid @RequestBody CreateCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(collectionService.createCollection(
            principal.userId(),
            request.getName(),
            request.getDescription(),
            Boolean.TRUE.equals(request.getPublicAccess())
        ));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CollectionDTO> updateCollection(
            @PathVariable String id,
            @RequestBody UpdateCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(collectionService.updateCollection(
            UUID.fromString(id), principal.userId(),
            request.getName(), request.getDescription(), request.getPublicAccess()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCollection(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        collectionService.deleteCollection(UUID.fromString(id), principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<List<CollectionItemDTO>> getItems(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(collectionService.getItems(UUID.fromString(id), principal.userId()));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<CollectionItemDTO> addItem(
            @PathVariable String id,
            @Valid @RequestBody AddItemRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(collectionService.addItem(
            UUID.fromString(id),
            UUID.fromString(request.getChatMessageId()),
            UUID.fromString(request.getChatId()),
            request.getNote(),
            principal.userId()
        ));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable String id,
            @PathVariable String itemId,
            @AuthenticationPrincipal UserPrincipal principal) {

        collectionService.removeItem(UUID.fromString(id), UUID.fromString(itemId), principal.userId());
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    static class CreateCollectionRequest {
        @NotBlank(message = "Name is required")
        private String name;
        private String description;
        private Boolean publicAccess;
    }

    @Data
    static class UpdateCollectionRequest {
        private String name;
        private String description;
        private Boolean publicAccess;
    }

    @Data
    static class AddItemRequest {
        @NotBlank(message = "chatMessageId is required")
        private String chatMessageId;
        @NotBlank(message = "chatId is required")
        private String chatId;
        private String note;
    }
}
