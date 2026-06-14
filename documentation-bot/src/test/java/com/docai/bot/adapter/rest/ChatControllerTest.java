package com.docai.bot.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.AllChatsResponse;
import com.docai.bot.application.service.ChatService.ChatResponse;
import com.docai.bot.application.service.FeedbackService;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.config.GlobalExceptionHandler;
import com.docai.bot.config.SecurityConfig;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Web-layer slice test for ChatController.
 * SecurityConfig is explicitly imported so the filter chain is active;
 * @MockitoBean covers all services/repositories the filters need.
 */
@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatService chatService;
    @MockitoBean FeedbackService feedbackService;
    @MockitoBean JwtService jwtService;
    @MockitoBean ApiKeyService apiKeyService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TenantRepository tenantRepository;

    @Test
    void query_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/chat/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void query_missingQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/chat/query")
                .with(user("alice").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void query_validRequest_returns200WithAnswer() throws Exception {
        ChatResponse fakeResponse = ChatResponse.builder()
            .chatId(UUID.randomUUID().toString())
            .messageId(UUID.randomUUID().toString())
            .answer("The answer is 42.")
            .sources(List.of())
            .confidence(0.85)
            .relatedQuestions(List.of())
            .build();
        when(chatService.processQuery(any())).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/chat/query")
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is the meaning of life?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("The answer is 42."))
            .andExpect(jsonPath("$.confidence").value(0.85));
    }

    @Test
    void getSessions_authenticated_returns200() throws Exception {
        when(chatService.getAllChatSessions(any(), any(Boolean.class)))
            .thenReturn(AllChatsResponse.builder().totalChats(0).sessions(List.of()).build());

        mockMvc.perform(get("/api/chat/sessions")
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalChats").value(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken userAuth(String username, String role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), username, role);
        return new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
