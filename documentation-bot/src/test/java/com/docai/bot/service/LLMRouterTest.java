package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import com.docai.bot.application.service.LLMProvider;
import com.docai.bot.application.service.LLMRouter;
import com.docai.bot.application.service.SecretsCryptoService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.TenantLLMConfig;
import com.docai.bot.domain.repository.TenantLLMConfigRepository;

/**
 * Covers LLMRouter's rerank-model resolution — the tenant's dedicated {@code rerankModel} field
 * that lets re-ranking use a different (typically cheaper) model than the simple/complex chat
 * split without touching answer generation.
 */
@ExtendWith(MockitoExtension.class)
class LLMRouterTest {

    @Mock LLMProvider openaiProvider;
    @Mock TenantLLMConfigRepository configRepository;
    @Mock SecretsCryptoService cryptoService;

    private LLMRouter router;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // LLMRouter indexes providers by name in its constructor, so the stub must exist before
        // construction — stubbing it later (e.g. inside stubConfig()) is too late.
        when(openaiProvider.providerName()).thenReturn("openai");
        router = new LLMRouter(List.of(openaiProvider), configRepository, cryptoService);
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void chatForRerank_dedicatedRerankModelSet_usesIt() {
        TenantLLMConfig config = baseConfig();
        config.setRerankModel("gpt-4o-nano");
        stubConfig(config);
        stubResponse("ranked");

        router.chatForRerank("prompt");

        ArgumentCaptor<LLMProvider.ChatSettings> settings = ArgumentCaptor.forClass(LLMProvider.ChatSettings.class);
        verify(openaiProvider).chat(eq(null), eq("prompt"), settings.capture());
        assertThat(settings.getValue().model()).isEqualTo("gpt-4o-nano");
    }

    @Test
    void chatForRerank_noRerankModel_routingEnabled_fallsBackToSimpleModel() {
        TenantLLMConfig config = baseConfig();
        config.setRoutingEnabled(true);
        config.setSimpleModel("gpt-4o-mini");
        config.setComplexModel("gpt-4o");
        config.setRerankModel(null);
        stubConfig(config);
        stubResponse("ranked");

        router.chatForRerank("prompt");

        ArgumentCaptor<LLMProvider.ChatSettings> settings = ArgumentCaptor.forClass(LLMProvider.ChatSettings.class);
        verify(openaiProvider).chat(eq(null), eq("prompt"), settings.capture());
        assertThat(settings.getValue().model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void chatForRerank_noRerankModel_routingDisabled_fallsBackToChatModel() {
        TenantLLMConfig config = baseConfig();
        config.setRoutingEnabled(false);
        config.setChatModel("gpt-4o-mini");
        config.setRerankModel(null);
        stubConfig(config);
        stubResponse("ranked");

        router.chatForRerank("prompt");

        ArgumentCaptor<LLMProvider.ChatSettings> settings = ArgumentCaptor.forClass(LLMProvider.ChatSettings.class);
        verify(openaiProvider).chat(eq(null), eq("prompt"), settings.capture());
        assertThat(settings.getValue().model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void chatForRerank_blankRerankModel_treatedAsUnset() {
        TenantLLMConfig config = baseConfig();
        config.setRoutingEnabled(false);
        config.setChatModel("gpt-4o-mini");
        config.setRerankModel("   ");
        stubConfig(config);
        stubResponse("ranked");

        router.chatForRerank("prompt");

        ArgumentCaptor<LLMProvider.ChatSettings> settings = ArgumentCaptor.forClass(LLMProvider.ChatSettings.class);
        verify(openaiProvider).chat(eq(null), eq("prompt"), settings.capture());
        assertThat(settings.getValue().model()).isEqualTo("gpt-4o-mini");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TenantLLMConfig baseConfig() {
        return TenantLLMConfig.builder()
            .tenantId(tenantId)
            .chatProvider("openai")
            .chatModel("gpt-4o-mini")
            .apiKeyEnc("encrypted-key")
            .temperature(0.7)
            .maxTokens(5000)
            .build();
    }

    private void stubConfig(TenantLLMConfig config) {
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(cryptoService.decrypt("encrypted-key")).thenReturn("sk-real-key");
    }

    private void stubResponse(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        ChatResponse response = new ChatResponse(List.of(generation), ChatResponseMetadata.builder().build());
        when(openaiProvider.chat(any(), any(), any())).thenReturn(response);
    }
}
