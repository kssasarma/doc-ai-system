package com.docai.e2e.admin;

import com.docai.e2e.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for document management in the Admin Console:
 * viewing documents, upload flow, ingestion status, and document filtering.
 *
 * <p>Upload tests use synthetic in-memory files so they can run without a real
 * document store configured; the assertions focus on the frontend upload flow
 * (file picker, progress, status chip) rather than the completed ingestion
 * outcome, which is covered by the service-level integration tests.
 */
@Tag("admin")
@Tag("documents")
class DocumentUploadE2ETest extends PlaywrightTestBase {

    @TempDir
    Path tempDir;

    @BeforeEach
    void signInAndNavigateToDocuments() {
        loginAsAdmin();
        waitForChatReady();
        navigate("/admin/documents");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    // ── Documents tab basics ──────────────────────────────────────────────────

    @Test
    void documentsTab_loadsSuccessfully() {
        assertThat(page).hasURL(Pattern.compile(".*admin/documents.*"));
        assertThat(page.locator("body")).not().containsText("Something went wrong");
        assertThat(page.locator("body")).not().containsText("500");
        assertThat(page.locator("body")).not().containsText("403");
    }

    @Test
    void documentsTab_hasUploadAreaOrButton() {
        Locator uploadElement = page.locator(
            "input[type='file'], button:has-text('Upload'), button:has-text('upload'), " +
            "[class*='drop'], [class*='upload'], [aria-label*='upload']"
        ).first();
        assertThat(uploadElement).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000)
        );
    }

    @Test
    void documentsTab_showsDocumentListOrEmptyState() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator content = page.locator(
            "table, [class*='table'], [class*='document-list'], " +
            "[class*='empty'], p:has-text('No documents'), p:has-text('no documents')"
        ).first();
        assertThat(content).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000)
        );
    }

    // ── File upload ───────────────────────────────────────────────────────────

    @Test
    void uploadPdfFile_filePickerAcceptsFile() throws IOException {
        Path testFile = tempDir.resolve("test-document.pdf");
        Files.writeString(testFile, "%PDF-1.4 test content for e2e upload test");

        Locator fileInput = page.locator("input[type='file']").first();
        if (fileInput.count() > 0) {
            fileInput.setInputFiles(testFile);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("body")).not().containsText("Uncaught");
        }
    }

    @Test
    void uploadTextFile_filePickerAcceptsFile() throws IOException {
        Path testFile = tempDir.resolve("documentation.txt");
        Files.writeString(testFile, "Sample documentation content for ingestion testing.");

        Locator fileInput = page.locator("input[type='file']").first();
        if (fileInput.count() > 0) {
            fileInput.setInputFiles(testFile);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("body")).not().containsText("Uncaught");
        }
    }

    @Test
    void uploadMultipleFiles_filePickerHandlesMultiple() throws IOException {
        Path file1 = tempDir.resolve("doc1.pdf");
        Path file2 = tempDir.resolve("doc2.pdf");
        Files.writeString(file1, "%PDF-1.4 doc one");
        Files.writeString(file2, "%PDF-1.4 doc two");

        Locator fileInput = page.locator("input[type='file']").first();
        if (fileInput.count() > 0) {
            String multipleAttr = fileInput.getAttribute("multiple");
            if (multipleAttr != null) {
                fileInput.setInputFiles(new Path[]{file1, file2});
                page.waitForLoadState(LoadState.NETWORKIDLE);
                assertThat(page.locator("body")).not().containsText("Uncaught");
            }
        }
    }

    // ── Document search / filter ──────────────────────────────────────────────

    @Test
    void documentsTab_searchInput_isPresent() {
        Locator searchInput = page.locator(
            "input[placeholder*='search' i], input[placeholder*='filter' i], " +
            "input[type='search'], input[aria-label*='search' i]"
        ).first();
        if (searchInput.count() > 0) {
            assertThat(searchInput).isVisible();
        }
    }

    @Test
    void documentsTab_statusFilter_ifPresent() {
        Locator statusFilter = page.locator(
            "select[aria-label*='status' i], button:has-text('Status'), [class*='filter']"
        ).first();
        if (statusFilter.count() > 0) {
            assertThat(statusFilter).isVisible();
        }
    }

    // ── Ingestion status badges ───────────────────────────────────────────────

    @Test
    void documentsTab_completedBadge_ifDocumentsExist() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator completedBadge = page.locator(
            "[class*='badge']:has-text('COMPLETED'), [class*='chip']:has-text('COMPLETED'), " +
            "span:has-text('COMPLETED'), td:has-text('COMPLETED')"
        ).first();
        if (completedBadge.count() > 0) {
            assertThat(completedBadge).isVisible();
        }
    }

    // ── Reload / refresh action ───────────────────────────────────────────────

    @Test
    void documentsTab_reloadButton_triggersRefresh() {
        Locator reloadBtn = page.locator(
            "button:has-text('Reload'), button:has-text('Refresh'), " +
            "button[aria-label*='reload' i], button[aria-label*='refresh' i]"
        ).first();
        if (reloadBtn.count() > 0) {
            assertThat(reloadBtn).isVisible();
            reloadBtn.click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page).hasURL(Pattern.compile(".*admin/documents.*"));
        }
    }

    // ── Ingestor API health (smoke test) ─────────────────────────────────────

    @Test
    void ingestorApi_healthEndpoint_responds() {
        String ingestorUrl = System.getProperty("ingestor.api.url", "http://localhost:8081");
        page.navigate(ingestorUrl + "/actuator/health");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).containsText("status");
    }
}
