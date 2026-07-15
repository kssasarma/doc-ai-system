package com.docai.ingestor.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.ingestor.application.service.DocumentQuotaService;
import com.docai.ingestor.application.service.DocumentStorageService;
import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.config.GlobalExceptionHandler;
import com.docai.ingestor.config.SecurityConfig;
import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.repository.DocumentRepository;

/**
 * Web-layer slice test for DocumentUploadController.
 * SecurityConfig is explicitly imported so the filter chain is active.
 * user() post-processors are used instead of @WithMockUser because the STATELESS
 * session policy causes SecurityContextHolderFilter to reload an empty context,
 * which clears @WithMockUser's thread-local authentication before it is evaluated.
 * They also bypass JwtTokenFilter's real JWT parsing entirely, so TenantContext is never
 * populated by the filter chain in this test — each test that reaches the controller's tenant-
 * scoped body sets it directly.
 */
@WebMvcTest(DocumentUploadController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQtbm90LXVzZWQtZm9yLXJlYWwtdG9rZW5zLTEyMzQ1Ng==")
class DocumentUploadControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentRepository documentRepository;
    @MockitoBean IngestionService ingestionService;
    @MockitoBean DocumentStorageService documentStorageService;
    @MockitoBean DocumentQuotaService documentQuotaService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        when(documentRepository.countByTenantIdAndStatusNot(TENANT_ID, IngestionStatus.FAILED)).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_roleUser_returns403() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0")
                .with(user("user").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void upload_adminEmptyFile_returns400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "test.pdf",
            MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(empty)
                .param("product", "prod")
                .param("version", "1.0")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    void upload_adminInvalidExtension_returns400() throws Exception {
        // .docx has no DocumentParser implementation, unlike .pdf/.chm/.html/.htm/.txt/.md.
        MockMultipartFile docxFile = new MockMultipartFile("file", "test.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "some content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(docxFile)
                .param("product", "prod")
                .param("version", "1.0")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                org.hamcrest.Matchers.containsString("Unsupported file type")));
    }

    @Test
    void upload_adminValidPdf_returns202WithDocumentId() throws Exception {
        UUID docId = UUID.randomUUID();
        Document saved = Document.builder()
            .tenantId(TENANT_ID)
            .product("prod").version("1.0")
            .documentName("test").fileHash("abc").fileType("pdf")
            .storageKey("documents/" + TENANT_ID + "/generated-key.pdf")
            .storageType("S3")
            .status(IngestionStatus.PROCESSING)
            .build();
        setId(saved, docId);

        when(documentStorageService.store(any(), any(), any(), anyLong())).thenReturn(saved.getStorageKey());
        when(documentStorageService.storageType()).thenReturn("S3");
        when(documentRepository.existsByFileHashAndTenantIdAndStatus(any(), any(), any())).thenReturn(false);
        when(documentRepository.findByFileHashAndTenantId(any(), any())).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenReturn(saved);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.message").value("Processing started"));
    }

    @Test
    void getAllDocuments_admin_returns200WithList() throws Exception {
        when(documentRepository.searchByTenantId(eq(TENANT_ID), any(), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        mockMvc.perform(get("/api/documents")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void upload_duplicateDocument_returns409() throws Exception {
        when(documentStorageService.store(any(), any(), any(), anyLong())).thenReturn("documents/" + TENANT_ID + "/dup.pdf");
        when(documentRepository.existsByFileHashAndTenantIdAndStatus(any(), any(), any())).thenReturn(true);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value(
                org.hamcrest.Matchers.containsString("already been processed")));

        // The just-stored duplicate must be cleaned back up, not left orphaned in storage.
        org.mockito.Mockito.verify(documentStorageService).delete("documents/" + TENANT_ID + "/dup.pdf");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename,
            MediaType.APPLICATION_PDF_VALUE, "PDF content".getBytes());
    }

    private static void setId(Document doc, UUID id) {
        try {
            var field = doc.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(doc, id);
        } catch (Exception ignored) {}
    }
}
