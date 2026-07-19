package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.bot.application.service.BookmarkService;
import com.docai.bot.application.service.BookmarkService.BookmarkDTO;
import com.docai.bot.domain.entity.Bookmark;
import com.docai.bot.domain.repository.BookmarkRepository;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    private BookmarkService bookmarkService;

    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID CHAT_ID    = UUID.randomUUID();
    private static final UUID BOOKMARK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookmarkService = new BookmarkService(bookmarkRepository);
    }

    // ── createBookmark ────────────────────────────────────────────────────────

    @Test
    void createBookmark_newMessage_savesAndReturnsDTO() {
        when(bookmarkRepository.findByUserIdAndChatMessageId(USER_ID, MESSAGE_ID))
            .thenReturn(Optional.empty());
        when(bookmarkRepository.save(any())).thenAnswer(inv -> {
            Bookmark b = inv.getArgument(0);
            setId(b, BOOKMARK_ID);
            return b;
        });

        BookmarkDTO dto = bookmarkService.createBookmark(
            USER_ID, MESSAGE_ID, CHAT_ID, "Here is the excerpt", "My Title", "My note", new String[]{"tag1"});

        assertThat(dto.getChatMessageId()).isEqualTo(MESSAGE_ID.toString());
        assertThat(dto.getChatId()).isEqualTo(CHAT_ID.toString());
        assertThat(dto.getTitle()).isEqualTo("My Title");
        assertThat(dto.getNote()).isEqualTo("My note");
        assertThat(dto.getTags()).containsExactly("tag1");
    }

    @Test
    void createBookmark_alreadyBookmarked_returnsExistingWithoutDuplicate() {
        Bookmark existing = savedBookmark();
        when(bookmarkRepository.findByUserIdAndChatMessageId(USER_ID, MESSAGE_ID))
            .thenReturn(Optional.of(existing));

        BookmarkDTO dto = bookmarkService.createBookmark(
            USER_ID, MESSAGE_ID, CHAT_ID, "excerpt", "title", "note", null);

        verify(bookmarkRepository, never()).save(any());
        assertThat(dto.getId()).isEqualTo(BOOKMARK_ID.toString());
    }

    @Test
    void createBookmark_nullTags_storesEmptyArray() {
        when(bookmarkRepository.findByUserIdAndChatMessageId(USER_ID, MESSAGE_ID))
            .thenReturn(Optional.empty());
        ArgumentCaptor<Bookmark> captor = ArgumentCaptor.forClass(Bookmark.class);
        when(bookmarkRepository.save(captor.capture())).thenAnswer(inv -> {
            Bookmark b = inv.getArgument(0);
            setId(b, BOOKMARK_ID);
            return b;
        });

        bookmarkService.createBookmark(USER_ID, MESSAGE_ID, CHAT_ID, null, null, null, null);

        assertThat(captor.getValue().getTags()).isEmpty();
    }

    // ── getBookmarks ─────────────────────────────────────────────────────────

    @Test
    void getBookmarks_returnsListInOrder() {
        when(bookmarkRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
            .thenReturn(List.of(savedBookmark()));

        List<BookmarkDTO> bookmarks = bookmarkService.getBookmarks(USER_ID);

        assertThat(bookmarks).hasSize(1);
        assertThat(bookmarks.get(0).getId()).isEqualTo(BOOKMARK_ID.toString());
    }

    @Test
    void getBookmarks_emptyWhenNone() {
        when(bookmarkRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
            .thenReturn(List.of());

        assertThat(bookmarkService.getBookmarks(USER_ID)).isEmpty();
    }

    // ── deleteBookmark ────────────────────────────────────────────────────────

    @Test
    void deleteBookmark_existingOwnedBookmark_deletesIt() {
        Bookmark bookmark = savedBookmark();
        when(bookmarkRepository.findByIdAndUserId(BOOKMARK_ID, USER_ID))
            .thenReturn(Optional.of(bookmark));

        bookmarkService.deleteBookmark(BOOKMARK_ID, USER_ID);

        verify(bookmarkRepository).delete(bookmark);
    }

    @Test
    void deleteBookmark_notFound_doesNothing() {
        when(bookmarkRepository.findByIdAndUserId(BOOKMARK_ID, USER_ID))
            .thenReturn(Optional.empty());

        bookmarkService.deleteBookmark(BOOKMARK_ID, USER_ID);

        verify(bookmarkRepository, never()).delete(any());
    }

    @Test
    void deleteBookmarkByMessage_existingMessage_deletesIt() {
        Bookmark bookmark = savedBookmark();
        when(bookmarkRepository.findByUserIdAndChatMessageId(USER_ID, MESSAGE_ID))
            .thenReturn(Optional.of(bookmark));

        bookmarkService.deleteBookmarkByMessage(USER_ID, MESSAGE_ID);

        verify(bookmarkRepository).delete(bookmark);
    }

    // ── updateBookmark ────────────────────────────────────────────────────────

    @Test
    void updateBookmark_updatesNoteAndTags() {
        Bookmark bookmark = savedBookmark();
        when(bookmarkRepository.findByIdAndUserId(BOOKMARK_ID, USER_ID))
            .thenReturn(Optional.of(bookmark));
        when(bookmarkRepository.save(any())).thenReturn(bookmark);

        BookmarkDTO dto = bookmarkService.updateBookmark(BOOKMARK_ID, USER_ID, "New note", new String[]{"a", "b"});

        assertThat(bookmark.getNote()).isEqualTo("New note");
        assertThat(bookmark.getTags()).containsExactly("a", "b");
    }

    @Test
    void updateBookmark_notFound_throwsIllegalArgument() {
        when(bookmarkRepository.findByIdAndUserId(BOOKMARK_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.updateBookmark(BOOKMARK_ID, USER_ID, "note", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bookmark not found");
    }

    // ── isBookmarked ─────────────────────────────────────────────────────────

    @Test
    void isBookmarked_whenExists_returnsTrue() {
        when(bookmarkRepository.existsByUserIdAndChatMessageId(USER_ID, MESSAGE_ID)).thenReturn(true);
        assertThat(bookmarkService.isBookmarked(USER_ID, MESSAGE_ID)).isTrue();
    }

    @Test
    void isBookmarked_whenAbsent_returnsFalse() {
        when(bookmarkRepository.existsByUserIdAndChatMessageId(USER_ID, MESSAGE_ID)).thenReturn(false);
        assertThat(bookmarkService.isBookmarked(USER_ID, MESSAGE_ID)).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Bookmark savedBookmark() {
        Bookmark b = Bookmark.builder()
            .userId(USER_ID)
            .chatMessageId(MESSAGE_ID)
            .chatId(CHAT_ID)
            .messageExcerpt("excerpt")
            .title("Title")
            .note("Note")
            .tags(new String[]{"tag1"})
            .build();
        setId(b, BOOKMARK_ID);
        return b;
    }

    private static void setId(Bookmark b, UUID id) {
        try {
            var field = b.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(b, id);
        } catch (Exception ignored) {}
    }
}
