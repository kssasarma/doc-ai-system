package com.docai.bot.adapter.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AnswerUpvoteService;
import com.docai.bot.application.service.AnswerUpvoteService.UpvoteResult;
import com.docai.bot.config.UserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class AnswerUpvoteController {

    private final AnswerUpvoteService upvoteService;

    @PostMapping("/{messageId}/upvote")
    public ResponseEntity<UpvoteResult> toggleUpvote(
            @PathVariable String messageId,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(upvoteService.toggleUpvote(UUID.fromString(messageId), principal.userId()));
    }
}
