package com.docai.bot.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.ChatHistoryResponse;
import com.docai.bot.application.service.ChatService.ChatRequest;
import com.docai.bot.application.service.ChatService.ChatResponse;

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
    public ResponseEntity<ChatResponse> query(@Valid @RequestBody QueryRequest request) {
        
        ChatRequest chatRequest = ChatRequest.builder()
            .chatId(request.getChatId())
            .product(request.getProduct())
            .version(request.getVersion())
            .question(request.getQuestion())
            .build();
        
        ChatResponse response = chatService.processQuery(chatRequest);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{chatId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(@PathVariable String chatId) {
        ChatHistoryResponse history = chatService.getChatHistory(chatId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/sessions")
    public ResponseEntity<ChatService.AllChatsResponse> getAllChats() {
        ChatService.AllChatsResponse response = chatService.getAllChatSessions();
        return ResponseEntity.ok(response);
    }

    @Data
    static class QueryRequest {
        private String chatId;
        
        // Product and version are now optional - will be auto-detected from the question
        private String product;
        private String version;
        
        @NotBlank(message = "Question is required")
        private String question;
    }
}
