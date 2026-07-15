package com.docai.bot.adapter.rest;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.DocumentLibraryService;
import com.docai.bot.application.service.DocumentLibraryService.LibraryDocument;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.model.SearchScope;

import lombok.RequiredArgsConstructor;

/** Backs the "Google for the company" home/library surface (Phase 6.2) — every USER (not just
 * ADMIN) can see what they're able to ask about. */
@RestController
@RequiredArgsConstructor
public class LibraryController {

    private final DocumentAccessPolicy documentAccessPolicy;
    private final DocumentLibraryService documentLibraryService;

    /** Sorted most-recently-updated first — the frontend groups by product for the full /library
     * page, and takes the first few for the home screen's "recently updated" strip. */
    @GetMapping("/api/library")
    public List<LibraryDocument> listMyDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String q) {
        SearchScope scope = documentAccessPolicy.resolveScope(principal.userId(), TenantContext.get());
        return documentLibraryService.listAccessibleDocuments(scope, q);
    }
}
