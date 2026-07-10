package com.docai.bot.adapter.rest;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.ProductCatalogService;
import com.docai.bot.application.service.ProductCatalogService.ProductEntry;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.model.SearchScope;

import lombok.RequiredArgsConstructor;

/** Backs the chat UI's optional scope chip (Phase 7) — distinct product+version pairs the
 * authenticated caller can actually search, never anything outside their own access. */
@RestController
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;
    private final DocumentAccessPolicy documentAccessPolicy;

    @GetMapping("/api/products")
    public List<ProductEntry> listAccessibleProducts(@AuthenticationPrincipal UserPrincipal principal) {
        SearchScope scope = documentAccessPolicy.resolveScope(principal.userId(), TenantContext.get());
        return productCatalogService.listAccessibleProducts(scope);
    }
}
