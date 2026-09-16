package com.docai.ingestor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.ingestor.application.service.ConfluenceConnectorService;
import com.docai.ingestor.application.service.DocumentQuotaService;
import com.docai.ingestor.application.service.DocumentStorageService;
import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.config.SafeUrlValidator;
import com.docai.ingestor.domain.entity.ConnectorSyncPage;
import com.docai.ingestor.domain.entity.IntegrationToken;
import com.docai.ingestor.domain.repository.ConnectorSyncPageRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.IntegrationTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.ApplicationEventPublisher;

/** Covers ConfluenceConnectorService#deleteSpaceContent — purging every document ingested from a
 * Confluence space, and the sync-history rows tracking them, without touching anything outside
 * that space. */
@ExtendWith(MockitoExtension.class)
class ConfluenceConnectorServiceTest {

    @Mock private IntegrationTokenRepository tokenRepository;
    @Mock private ConnectorSyncPageRepository syncPageRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentStorageService documentStorageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ObjectMapper objectMapper;
    @Mock private SafeUrlValidator safeUrlValidator;
    @Mock private DocumentQuotaService documentQuotaService;
    @Mock private IngestionService ingestionService;

    private ConfluenceConnectorService service;

    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String SPACE_KEY = "ENG";

    @BeforeEach
    void setUp() {
        service = new ConfluenceConnectorService(
            tokenRepository, syncPageRepository, documentRepository, documentStorageService,
            eventPublisher, objectMapper, safeUrlValidator, documentQuotaService, ingestionService);

        lenient().when(tokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(
            IntegrationToken.builder().id(TOKEN_ID).tenantId(TENANT_ID).build()));
    }

    @Test
    void deleteSpaceContent_deletesEachPagesDocumentAndAllSyncRecords() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        List<ConnectorSyncPage> pages = List.of(
            page(doc1), page(doc2));
        when(syncPageRepository.findByTokenIdAndSpaceKey(TOKEN_ID, SPACE_KEY)).thenReturn(pages);

        int deleted = service.deleteSpaceContent(TOKEN_ID, SPACE_KEY);

        assertThat(deleted).isEqualTo(2);
        verify(ingestionService).deleteDocument(doc1, TENANT_ID);
        verify(ingestionService).deleteDocument(doc2, TENANT_ID);
        verify(syncPageRepository).deleteAll(pages);
    }

    @Test
    void deleteSpaceContent_skipsPagesWithNoDocument() {
        ConnectorSyncPage neverIngested = page(null);
        when(syncPageRepository.findByTokenIdAndSpaceKey(TOKEN_ID, SPACE_KEY))
            .thenReturn(List.of(neverIngested));

        int deleted = service.deleteSpaceContent(TOKEN_ID, SPACE_KEY);

        assertThat(deleted).isZero();
        verify(ingestionService, never()).deleteDocument(any(), any());
        verify(syncPageRepository).deleteAll(List.of(neverIngested));
    }

    @Test
    void deleteSpaceContent_documentAlreadyGone_stillRemovesSyncRecordAndContinues() {
        UUID goneDoc = UUID.randomUUID();
        UUID liveDoc = UUID.randomUUID();
        List<ConnectorSyncPage> pages = List.of(page(goneDoc), page(liveDoc));
        when(syncPageRepository.findByTokenIdAndSpaceKey(TOKEN_ID, SPACE_KEY)).thenReturn(pages);
        doThrow(new IllegalArgumentException("Document not found: " + goneDoc))
            .when(ingestionService).deleteDocument(eq(goneDoc), eq(TENANT_ID));

        int deleted = service.deleteSpaceContent(TOKEN_ID, SPACE_KEY);

        assertThat(deleted).isEqualTo(1);
        verify(ingestionService).deleteDocument(liveDoc, TENANT_ID);
        verify(syncPageRepository).deleteAll(pages);
    }

    @Test
    void deleteSpaceContent_onlyTargetsTheGivenTokenAndSpace() {
        ArgumentCaptor<UUID> tokenIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> spaceKeyCaptor = ArgumentCaptor.forClass(String.class);
        when(syncPageRepository.findByTokenIdAndSpaceKey(any(), any())).thenReturn(List.of());

        service.deleteSpaceContent(TOKEN_ID, SPACE_KEY);

        verify(syncPageRepository).findByTokenIdAndSpaceKey(tokenIdCaptor.capture(), spaceKeyCaptor.capture());
        assertThat(tokenIdCaptor.getValue()).isEqualTo(TOKEN_ID);
        assertThat(spaceKeyCaptor.getValue()).isEqualTo(SPACE_KEY);
    }

    @Test
    void deleteSpaceContent_tokenNotFound_throws() {
        UUID missingToken = UUID.randomUUID();
        when(tokenRepository.findById(missingToken)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.deleteSpaceContent(missingToken, SPACE_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static ConnectorSyncPage page(UUID documentId) {
        return ConnectorSyncPage.builder()
            .id(UUID.randomUUID())
            .tokenId(TOKEN_ID)
            .provider("confluence")
            .externalId(UUID.randomUUID().toString())
            .spaceKey(SPACE_KEY)
            .documentId(documentId)
            .build();
    }
}
