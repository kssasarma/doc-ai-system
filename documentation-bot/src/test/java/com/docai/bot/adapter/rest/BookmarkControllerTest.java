package com.docai.bot.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.BookmarkService;
import com.docai.bot.application.service.BookmarkService.BookmarkDTO;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.config.GlobalExceptionHandler;
import com.docai.bot.config.SecurityConfig;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

@WebMvcTest(BookmarkController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BookmarkControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean BookmarkService bookmarkService;

    // Filter dependencies
    @MockitoBean JwtService jwtService;
    @MockitoBean ApiKeyService apiKeyService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TenantRepository tenantRepository;

    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID MESSAGE_ID  = UUID.randomUUID();
    private static final UUID CHAT_ID     = UUID.randomUUID();
    private static final UUID BOOKMARK_ID = UUID.randomUUID();

    // ── GET /api/bookmarks ────────────────────────────────────────────────────

    @Test
    void getBookmarks_authenticated_returns200WithList() throws Exception {
        BookmarkDTO dto = BookmarkDTO.builder()
            .id(BOOKMARK_ID.toString())
            .chatMessageId(MESSAGE_ID.toString())
            .chatId(CHAT_ID.toString())
            .messageExcerpt("excerpt text")
            .title("My Bookmark")
            .note("Important")
            .tags(new String[]{"important"})
            .build();
        when(bookmarkService.getBookmarks(USER_ID)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/bookmarks")
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("My Bookmark"))
            .andExpect(jsonPath("$[0].note").value("Important"));
    }

    @Test
    void getBookmarks_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/bookmarks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getBookmarks_emptyList_returns200() throws Exception {
        when(bookmarkService.getBookmarks(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookmarks")
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/bookmarks ───────────────────────────────────────────────────

    @Test
    void createBookmark_validRequest_returns200() throws Exception {
        BookmarkDTO dto = BookmarkDTO.builder()
            .id(BOOKMARK_ID.toString())
            .chatMessageId(MESSAGE_ID.toString())
            .chatId(CHAT_ID.toString())
            .messageExcerpt("excerpt")
            .title("Title")
            .note("note")
            .tags(new String[]{})
            .build();
        when(bookmarkService.createBookmark(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(dto);

        String body = """
            {
              "chatMessageId": "%s",
              "chatId": "%s",
              "messageExcerpt": "excerpt",
              "title": "Title",
              "note": "note"
            }
            """.formatted(MESSAGE_ID, CHAT_ID);

        mockMvc.perform(post("/api/bookmarks")
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(BOOKMARK_ID.toString()))
            .andExpect(jsonPath("$.title").value("Title"));
    }

    @Test
    void createBookmark_missingChatMessageId_returns400() throws Exception {
        String body = """
            {
              "chatId": "%s",
              "title": "Title"
            }
            """.formatted(CHAT_ID);

        mockMvc.perform(post("/api/bookmarks")
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createBookmark_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/bookmarks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chatMessageId\":\"id\",\"chatId\":\"id\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/bookmarks/{id} ─────────────────────────────────────────────

    @Test
    void updateBookmark_validRequest_returns200() throws Exception {
        BookmarkDTO updated = BookmarkDTO.builder()
            .id(BOOKMARK_ID.toString())
            .chatMessageId(MESSAGE_ID.toString())
            .chatId(CHAT_ID.toString())
            .note("Updated note")
            .tags(new String[]{"tag1", "tag2"})
            .build();
        when(bookmarkService.updateBookmark(eq(BOOKMARK_ID), eq(USER_ID), any(), any()))
            .thenReturn(updated);

        mockMvc.perform(patch("/api/bookmarks/{id}", BOOKMARK_ID)
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Updated note\",\"tags\":[\"tag1\",\"tag2\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.note").value("Updated note"));
    }

    // ── DELETE /api/bookmarks/{id} ────────────────────────────────────────────

    @Test
    void deleteBookmark_authenticated_returns204() throws Exception {
        doNothing().when(bookmarkService).deleteBookmark(BOOKMARK_ID, USER_ID);

        mockMvc.perform(delete("/api/bookmarks/{id}", BOOKMARK_ID)
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteBookmark_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/bookmarks/{id}", BOOKMARK_ID))
            .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/bookmarks/by-message/{chatMessageId} ─────────────────────

    @Test
    void deleteBookmarkByMessage_authenticated_returns204() throws Exception {
        doNothing().when(bookmarkService).deleteBookmarkByMessage(USER_ID, MESSAGE_ID);

        mockMvc.perform(delete("/api/bookmarks/by-message/{chatMessageId}", MESSAGE_ID)
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isNoContent());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken userAuth(String username, String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, username, role, TENANT_ID, false);
        return new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
