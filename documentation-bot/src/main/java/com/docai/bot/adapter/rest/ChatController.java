package com.docai.bot.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.AllChatsResponse;
import com.docai.bot.application.service.ChatService.ChatHistoryResponse;
import com.docai.bot.application.service.ChatService.ChatRequest;
import com.docai.bot.application.service.ChatService.ChatResponse;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/query")
    public ResponseEntity<ChatResponse> query(
            @Valid @RequestBody QueryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        ChatRequest chatRequest = ChatRequest.builder()
            .chatId(request.getChatId())
            .product(request.getProduct())
            .version(request.getVersion())
            .question(request.getQuestion())
            .userId(principal.userId())
            .build();

        return ResponseEntity.ok(chatService.processQuery(chatRequest));
    }

    @GetMapping("/history/{chatId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(@PathVariable String chatId) {
        return ResponseEntity.ok(chatService.getChatHistory(chatId));
    }

    @GetMapping("/sessions")
    public ResponseEntity<AllChatsResponse> getAllChats(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(chatService.getAllChatSessions(principal.userId(), principal.isAdmin()));
    }

    @DeleteMapping("/sessions/{chatId}")
    public ResponseEntity<Void> deleteChatSession(@PathVariable String chatId) {
        chatService.deleteChatSession(chatId);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class QueryRequest {
        private String chatId;
        private String product;
        private String version;
        @NotBlank(message = "Question is required")
        private String question;
    }
}
