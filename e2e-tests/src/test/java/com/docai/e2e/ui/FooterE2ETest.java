package com.docai.e2e.ui;

import com.docai.e2e.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests verifying the attribution footer is present and correct on every
 * layout surface: the auth pages (no login required), the main app, and the
 * admin console.
 */
@Tag("ui")
class FooterE2ETest extends PlaywrightTestBase {

    private static final String AUTHOR_TEXT     = "Satya Kodamanchili";
    private static final String LINKEDIN_URL    = "https://www.linkedin.com/in/surya-kodamanchili/";
    private static final String GITHUB_URL      = "https://github.com/kssasarma";
    private static final String GITHUB_DISPLAY  = "github.com/kssasarma";

    // ── Auth layout (no credentials needed) ──────────────────────────────────

    @Test
    void loginPage_footer_isPresentWithAttributionText() {
        navigate("/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator footer = page.locator("footer");
        assertThat(footer).isVisible();
        assertThat(footer).containsText("A side project by");
        assertThat(footer).containsText(AUTHOR_TEXT);
    }

    @Test
    void loginPage_footer_hasLinkedInLink() {
        navigate("/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator link = page.locator("footer a[href='" + LINKEDIN_URL + "']");
        assertThat(link).isVisible();
        assertThat(link).containsText(AUTHOR_TEXT);
    }

    @Test
    void loginPage_footer_hasGitHubLink() {
        navigate("/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator link = page.locator("footer a[href='" + GITHUB_URL + "']");
        assertThat(link).isVisible();
        assertThat(link).containsText(GITHUB_DISPLAY);
    }

    @Test
    void forgotPasswordPage_footer_isPresent() {
        navigate("/forgot-password");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("footer")).isVisible();
        assertThat(page.locator("footer")).containsText(AUTHOR_TEXT);
    }

    // ── Main app (AppShell) ───────────────────────────────────────────────────

    @Test
    void mainApp_footer_isPresentAfterLogin() {
        loginAsAdmin();
        waitForChatReady();

        Locator footer = page.locator("footer");
        assertThat(footer).isVisible();
        assertThat(footer).containsText("A side project by");
        assertThat(footer).containsText(AUTHOR_TEXT);
    }

    @Test
    void mainApp_footer_linkedInLinkOpensCorrectHref() {
        loginAsAdmin();
        waitForChatReady();

        Locator link = page.locator("footer a[href='" + LINKEDIN_URL + "']");
        assertThat(link).isVisible();
    }

    @Test
    void mainApp_footer_gitHubLinkOpensCorrectHref() {
        loginAsAdmin();
        waitForChatReady();

        Locator link = page.locator("footer a[href='" + GITHUB_URL + "']");
        assertThat(link).isVisible();
    }

    // ── Admin console (AdminLayout) ───────────────────────────────────────────

    @Test
    void adminConsole_footer_isPresentAfterLogin() {
        loginAsAdmin();
        navigate("/admin");
        waitForAdminConsole();

        Locator footer = page.locator("footer");
        assertThat(footer).isVisible();
        assertThat(footer).containsText("A side project by");
        assertThat(footer).containsText(AUTHOR_TEXT);
    }

    @Test
    void adminConsole_footer_hasCorrectLinks() {
        loginAsAdmin();
        navigate("/admin");
        waitForAdminConsole();

        assertThat(page.locator("footer a[href='" + LINKEDIN_URL + "']")).isVisible();
        assertThat(page.locator("footer a[href='" + GITHUB_URL + "']")).isVisible();
    }
}
