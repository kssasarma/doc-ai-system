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

import com.docai.bot.application.service.BookmarkService;
import com.docai.bot.application.service.BookmarkService.BookmarkDTO;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping
    public ResponseEntity<List<BookmarkDTO>> getBookmarks(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(bookmarkService.getBookmarks(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<BookmarkDTO> createBookmark(
            @Valid @RequestBody CreateBookmarkRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookmarkDTO dto = bookmarkService.createBookmark(
            principal.userId(),
            UUID.fromString(request.getChatMessageId()),
            UUID.fromString(request.getChatId()),
            request.getMessageExcerpt(),
            request.getTitle(),
            request.getNote(),
            request.getTags()
        );
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{bookmarkId}")
    public ResponseEntity<BookmarkDTO> updateBookmark(
            @PathVariable UUID bookmarkId,
            @RequestBody UpdateBookmarkRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(
            bookmarkService.updateBookmark(bookmarkId, principal.userId(),
                request.getNote(), request.getTags())
        );
    }

    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<Void> deleteBookmark(
            @PathVariable UUID bookmarkId,
            @AuthenticationPrincipal UserPrincipal principal) {

        bookmarkService.deleteBookmark(bookmarkId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    // Lets the chat view toggle a message's bookmark off without first looking up the
    // bookmark's own id — it only ever knows the chatMessageId it's rendering.
    @DeleteMapping("/by-message/{chatMessageId}")
    public ResponseEntity<Void> deleteBookmarkByMessage(
            @PathVariable UUID chatMessageId,
            @AuthenticationPrincipal UserPrincipal principal) {

        bookmarkService.deleteBookmarkByMessage(principal.userId(), chatMessageId);
        return ResponseEntity.noContent().build();
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    @Data
    static class CreateBookmarkRequest {
        @NotBlank
        private String chatMessageId;
        @NotBlank
        private String chatId;
        private String messageExcerpt;
        private String title;
        private String note;
        private String[] tags;
    }

    @Data
    static class UpdateBookmarkRequest {
        private String note;
        private String[] tags;
    }
}
