package com.docai.bot.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.AllChatsResponse;
import com.docai.bot.application.service.ChatService.ChatResponse;
import com.docai.bot.application.service.FeedbackService;
import com.docai.bot.config.GlobalExceptionHandler;

/**
 * Web-layer slice test for ChatController.
 * No Spring context start-up — only the MVC layer, security, and the handler under test.
 */
@WebMvcTest(ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatService chatService;
    @MockitoBean FeedbackService feedbackService;

    @Test
    void query_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/chat/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void query_missingQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/chat/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
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
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is the meaning of life?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("The answer is 42."))
            .andExpect(jsonPath("$.confidence").value(0.85));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void getSessions_authenticated_returns200() throws Exception {
        when(chatService.getAllChatSessions(any(), any(Boolean.class)))
            .thenReturn(AllChatsResponse.builder().totalChats(0).sessions(List.of()).build());

        mockMvc.perform(get("/api/chat/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalChats").value(0));
    }
}
