package com.docai.bot.adapter.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.SharedChatService;
import com.docai.bot.application.service.SharedChatService.ShareLinkDTO;
import com.docai.bot.application.service.SharedChatService.SharedChatViewDTO;
import com.docai.bot.config.UserPrincipal;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SharedChatController {

    private final SharedChatService sharedChatService;

    // Authenticated: create a share link for a session
    @PostMapping("/api/chat/sessions/{chatId}/share")
    public ResponseEntity<ShareLinkDTO> createShareLink(
            @PathVariable String chatId,
            @RequestBody(required = false) ShareRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean publicAccess = request != null && Boolean.TRUE.equals(request.getPublicAccess());
        Integer expireDays = request != null ? request.getExpireDays() : null;

        return ResponseEntity.ok(sharedChatService.createShareLink(
            UUID.fromString(chatId), principal, publicAccess, expireDays));
    }

    // Authenticated: get current share link for a session
    @GetMapping("/api/chat/sessions/{chatId}/share")
    public ResponseEntity<ShareLinkDTO> getShareLink(
            @PathVariable String chatId,
            @AuthenticationPrincipal UserPrincipal principal) {

        ShareLinkDTO dto = sharedChatService.getShareLink(UUID.fromString(chatId), principal.userId());
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    // Authenticated: delete share link
    @DeleteMapping("/api/chat/sessions/{chatId}/share")
    public ResponseEntity<Void> deleteShareLink(
            @PathVariable String chatId,
            @AuthenticationPrincipal UserPrincipal principal) {

        sharedChatService.deleteShareLink(UUID.fromString(chatId), principal.userId());
        return ResponseEntity.noContent().build();
    }

    // Optionally authenticated: JWT is not required (public links must work for anonymous
    // visitors), but if one is present it's resolved so non-public ("team only") links can
    // check the viewer's identity/tenant — see SharedChatService.verifyViewAccess.
    @GetMapping("/api/share/{token}")
    public ResponseEntity<SharedChatViewDTO> getSharedChat(
            @PathVariable String token,
            @AuthenticationPrincipal(errorOnInvalidType = false) UserPrincipal principal) {
        return ResponseEntity.ok(sharedChatService.getSharedChat(token, principal));
    }

    // Authenticated: fork a shared chat into caller's account
    @PostMapping("/api/share/{token}/fork")
    public ResponseEntity<ForkResponse> forkSharedChat(
            @PathVariable String token,
            @AuthenticationPrincipal UserPrincipal principal) {

        String newChatId = sharedChatService.forkSharedChat(token, principal);
        return ResponseEntity.ok(new ForkResponse(newChatId));
    }

    @Data
    static class ShareRequest {
        private Boolean publicAccess;
        private Integer expireDays; // null = never expires
    }

    record ForkResponse(String chatId) {}
}
