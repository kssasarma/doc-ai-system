package com.docai.ingestor.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.config.GlobalExceptionHandler;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.repository.DocumentRepository;

/**
 * Web-layer slice test for DocumentUploadController.
 * Security is active; JWT filter runs but @WithMockUser bypasses token validation.
 */
@WebMvcTest(DocumentUploadController.class)
@Import(GlobalExceptionHandler.class)
class DocumentUploadControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentRepository documentRepository;
    @MockitoBean IngestionService ingestionService;

    @Test
    void upload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void upload_roleUser_returns403() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void upload_adminEmptyFile_returns400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "test.pdf",
            MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(empty)
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void upload_adminInvalidExtension_returns400() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt",
            MediaType.TEXT_PLAIN_VALUE, "some content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(txtFile)
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                org.hamcrest.Matchers.containsString("Unsupported file type")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void upload_adminValidPdf_returns202WithDocumentId() throws Exception {
        UUID docId = UUID.randomUUID();
        Document saved = Document.builder()
            .product("prod").version("1.0")
            .documentName("test").fileHash("abc").fileType("pdf")
            .filePath("/tmp/test.pdf").status(IngestionStatus.PROCESSING)
            .build();
        setId(saved, docId);

        when(ingestionService.calculateFileHash(any())).thenReturn("abc123hash");
        when(documentRepository.existsByFileHashAndStatus(any(), any())).thenReturn(false);
        when(documentRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenReturn(saved);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.message").value("Processing started"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllDocuments_admin_returns200WithList() throws Exception {
        when(documentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void upload_duplicateDocument_returns409() throws Exception {
        when(ingestionService.calculateFileHash(any())).thenReturn("existinghash");
        when(documentRepository.existsByFileHashAndStatus(any(), any())).thenReturn(true);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(pdfFile("test.pdf"))
                .param("product", "prod")
                .param("version", "1.0"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value(
                org.hamcrest.Matchers.containsString("already been processed")));
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
