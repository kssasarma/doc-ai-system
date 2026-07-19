package com.docai.ingestor.adapter.rest;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.ingestor.config.GlobalExceptionHandler;
import com.docai.ingestor.config.SecurityConfig;
import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.repository.DocumentRepository;

/**
 * Web-layer slice test for IngestionController.
 * All endpoints require ROLE_ADMIN — tests cover auth, status counts, and document listing.
 */
@WebMvcTest(IngestionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
    "app.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQtbm90LXVzZWQtZm9yLXJlYWwtdG9rZW5zLTEyMzQ1Ng==",
    "app.webhook.hmac-secret=test-webhook-secret",
    "app.internal.service-secret=test-internal-secret"
})
class IngestionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentRepository documentRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── POST /api/ingest/reload ────────────────────────────────────────────────

    @Test
    void reload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/ingest/reload"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reload_roleUser_returns403() throws Exception {
        mockMvc.perform(post("/api/ingest/reload")
                .with(user("user").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void reload_admin_returns200WithHelpMessage() throws Exception {
        mockMvc.perform(post("/api/ingest/reload")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").exists());
    }

    // ── GET /api/ingest/status ────────────────────────────────────────────────

    @Test
    void getStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ingest/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getStatus_adminNoDocuments_returns200WithZeroCounts() throws Exception {
        when(documentRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/ingest/status")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDocuments").value(0))
            .andExpect(jsonPath("$.completed").value(0))
            .andExpect(jsonPath("$.processing").value(0))
            .andExpect(jsonPath("$.failed").value(0))
            .andExpect(jsonPath("$.pending").value(0))
            .andExpect(jsonPath("$.totalChunks").value(0));
    }

    @Test
    void getStatus_adminMixedDocuments_returnsCorrectCounts() throws Exception {
        List<Document> docs = List.of(
            doc(IngestionStatus.COMPLETED, 10),
            doc(IngestionStatus.COMPLETED, 20),
            doc(IngestionStatus.PROCESSING, null),
            doc(IngestionStatus.FAILED, null),
            doc(IngestionStatus.PENDING, null)
        );
        when(documentRepository.findByTenantId(TENANT_ID)).thenReturn(docs);

        mockMvc.perform(get("/api/ingest/status")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDocuments").value(5))
            .andExpect(jsonPath("$.completed").value(2))
            .andExpect(jsonPath("$.processing").value(1))
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.pending").value(1))
            .andExpect(jsonPath("$.totalChunks").value(30));
    }

    @Test
    void getStatus_adminCompletedDocuments_sumsTotalChunks() throws Exception {
        List<Document> docs = List.of(
            doc(IngestionStatus.COMPLETED, 100),
            doc(IngestionStatus.COMPLETED, 250)
        );
        when(documentRepository.findByTenantId(TENANT_ID)).thenReturn(docs);

        mockMvc.perform(get("/api/ingest/status")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalChunks").value(350));
    }

    // ── GET /api/ingest/documents ──────────────────────────────────────────────

    @Test
    void getAllDocuments_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ingest/documents"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllDocuments_adminNoDocuments_returns200WithEmptyList() throws Exception {
        when(documentRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/ingest/documents")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllDocuments_adminWithDocuments_returnsDocumentInfo() throws Exception {
        Document doc = completedDoc("Installation Guide", "product-x", "2.0", 42);
        when(documentRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/ingest/documents")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documentName").value("Installation Guide"))
            .andExpect(jsonPath("$[0].product").value("product-x"))
            .andExpect(jsonPath("$[0].version").value("2.0"))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[0].chunkCount").value(42));
    }

    @Test
    void getAllDocuments_failedDoc_includesErrorMessage() throws Exception {
        Document doc = Document.builder()
            .tenantId(TENANT_ID)
            .product("product-y")
            .version("1.0")
            .documentName("Broken Doc")
            .fileHash("hash")
            .fileType("pdf")
            .storageKey("key")
            .storageType("S3")
            .status(IngestionStatus.FAILED)
            .errorMessage("Parser failed: corrupted PDF")
            .build();
        setId(doc, UUID.randomUUID());

        when(documentRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/ingest/documents")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("FAILED"))
            .andExpect(jsonPath("$[0].errorMessage").value("Parser failed: corrupted PDF"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Document doc(IngestionStatus status, Integer chunkCount) {
        Document d = Document.builder()
            .tenantId(TENANT_ID)
            .product("prod")
            .version("1.0")
            .documentName("doc")
            .fileHash("hash-" + UUID.randomUUID())
            .fileType("pdf")
            .storageKey("key")
            .storageType("S3")
            .status(status)
            .chunkCount(chunkCount)
            .build();
        setId(d, UUID.randomUUID());
        return d;
    }

    private static Document completedDoc(String name, String product, String version, int chunks) {
        Document d = Document.builder()
            .tenantId(TENANT_ID)
            .product(product)
            .version(version)
            .documentName(name)
            .fileHash("hash-" + UUID.randomUUID())
            .fileType("pdf")
            .storageKey("key")
            .storageType("S3")
            .status(IngestionStatus.COMPLETED)
            .chunkCount(chunks)
            .build();
        setId(d, UUID.randomUUID());
        return d;
    }

    private static void setId(Document doc, UUID id) {
        try {
            var field = doc.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(doc, id);
        } catch (Exception ignored) {}
    }
}
