package com.docai.bot.adapter.rest;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.AllChatsResponse;
import com.docai.bot.application.service.ChatService.ChatHistoryResponse;
import com.docai.bot.application.service.ChatService.ChatRequest;
import com.docai.bot.application.service.ChatService.ChatResponse;
import com.docai.bot.application.service.ChatService.SessionUpdateResponse;
import com.docai.bot.application.service.FeedbackService;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final FeedbackService feedbackService;

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

    /**
     * Streaming counterpart of {@code POST /query} — same request shape, but the answer arrives
     * token-by-token over Server-Sent Events instead of one blocking JSON response. See
     * {@link ChatService#processQueryStream} for the full event contract. No read timeout (0) —
     * a slow-but-progressing generation shouldn't be killed by an emitter timeout; the client
     * (fetch AbortController) is what ends the connection early ("stop generating").
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(
            @Valid @RequestBody QueryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        ChatRequest chatRequest = ChatRequest.builder()
            .chatId(request.getChatId())
            .product(request.getProduct())
            .version(request.getVersion())
            .question(request.getQuestion())
            .userId(principal.userId())
            .build();

        SseEmitter emitter = new SseEmitter(0L);
        chatService.processQueryStream(chatRequest, emitter);
        return emitter;
    }

    @GetMapping("/history/{chatId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(
            @PathVariable String chatId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(chatService.getChatHistory(chatId, principal));
    }

    @GetMapping("/sessions")
    public ResponseEntity<AllChatsResponse> getAllChats(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(chatService.getAllChatSessions(principal));
    }

    @PatchMapping("/sessions/{chatId}")
    public ResponseEntity<SessionUpdateResponse> updateSession(
            @PathVariable String chatId,
            @RequestBody SessionPatchRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(chatService.updateSession(
            chatId, principal,
            request.getTitle(), request.getPinned(), request.getTags()
        ));
    }

    @DeleteMapping("/sessions/{chatId}")
    public ResponseEntity<Void> deleteChatSession(
            @PathVariable String chatId,
            @AuthenticationPrincipal UserPrincipal principal) {
        chatService.deleteChatSession(chatId, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{chatId}/export")
    public ResponseEntity<String> exportSession(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "markdown") String format,
            @AuthenticationPrincipal UserPrincipal principal) {

        String content = chatService.exportSession(chatId, principal, format);
        boolean isJson = "json".equalsIgnoreCase(format);
        String filename = "chat-" + chatId.substring(0, 8) + (isJson ? ".json" : ".md");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
            .headers(headers)
            .contentType(isJson ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN)
            .body(content);
    }

    @PostMapping("/messages/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable String messageId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        feedbackService.submitFeedback(
            UUID.fromString(messageId),
            principal,
            request.getRating(),
            request.getFeedbackText()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/messages/{messageId}/regenerate")
    public ResponseEntity<ChatResponse> regenerateAnswer(
            @PathVariable String messageId,
            @RequestBody(required = false) RegenerateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        String style = request != null && request.getStyle() != null ? request.getStyle() : "BALANCED";
        return ResponseEntity.ok(chatService.regenerateAnswer(messageId, principal, style));
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    @Data
    static class QueryRequest {
        private String chatId;
        private String product;
        private String version;
        @NotBlank(message = "Question is required")
        private String question;
    }

    @Data
    static class SessionPatchRequest {
        private String title;
        private Boolean pinned;
        private String[] tags;
    }

    @Data
    static class FeedbackRequest {
        @NotNull(message = "Rating is required")
        @Min(value = -1, message = "Rating must be -1 or 1")
        @Max(value = 1,  message = "Rating must be -1 or 1")
        private Integer rating;
        private String feedbackText;
    }

    @Data
    static class RegenerateRequest {
        private String style; // CONCISE | DETAILED | CODE_FIRST | BALANCED
    }
}
