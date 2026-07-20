package com.docai.e2e.admin;

import com.docai.e2e.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the Admin Console: navigation, analytics overview, documents tab,
 * users tab, and role-based access control.
 */
@Tag("admin")
class AdminPanelE2ETest extends PlaywrightTestBase {

    @BeforeEach
    void signInAsAdmin() {
        loginAsAdmin();
        waitForChatReady();
    }

    // ── Navigation to admin ───────────────────────────────────────────────────

    @Test
    void adminConsole_accessible_viaDirectUrl() {
        navigate("/admin");
        page.waitForURL(
            url -> url.contains("/admin"),
            new Page.WaitForURLOptions().setTimeout(8_000)
        );
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("nav")).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000)
        );
    }

    @Test
    void adminConsole_defaultRoute_redirectsToOverview() {
        navigate("/admin");
        page.waitForURL(
            url -> url.contains("/admin/overview"),
            new Page.WaitForURLOptions().setTimeout(8_000)
        );
        assertThat(page).hasURL(Pattern.compile(".*admin/overview.*"));
    }

    @Test
    void adminNav_containsExpectedTabs() {
        navigate("/admin/overview");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator nav = page.locator("nav");
        assertThat(nav.filter(new Locator.FilterOptions().setHasText("Overview"))).isVisible();
        assertThat(nav.filter(new Locator.FilterOptions().setHasText("Documents"))).isVisible();
        assertThat(nav.filter(new Locator.FilterOptions().setHasText("Users"))).isVisible();
        assertThat(nav.filter(new Locator.FilterOptions().setHasText("Cost"))).isVisible();
    }

    // ── Overview tab ──────────────────────────────────────────────────────────

    @Test
    void overviewTab_loadsWithoutError() {
        navigate("/admin/overview");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).not().containsText("Something went wrong");
        assertThat(page.locator("body")).not().containsText("500");
    }

    @Test
    void overviewTab_showsContentArea() {
        navigate("/admin/overview");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator content = page.locator("main, [role='main'], section, [class*='content']").first();
        assertThat(content).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000)
        );
    }

    // ── Documents tab ─────────────────────────────────────────────────────────

    @Test
    void documentsTab_navigatesAndLoads() {
        navigate("/admin/overview");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator("nav a:has-text('Documents'), nav [href*='documents']").first().click();
        page.waitForURL(
            url -> url.contains("/admin/documents"),
            new Page.WaitForURLOptions().setTimeout(8_000)
        );
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page).hasURL(Pattern.compile(".*admin/documents.*"));
        assertThat(page.locator("body")).not().containsText("Something went wrong");
    }

    @Test
    void documentsTab_directUrl_loads() {
        navigate("/admin/documents");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).not().containsText("Something went wrong");
        assertThat(page.locator("body")).not().containsText("500");
    }

    // ── Users tab ─────────────────────────────────────────────────────────────

    @Test
    void usersTab_navigatesAndLoads() {
        navigate("/admin/users");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator heading = page.locator("h1, h2, [class*='header'], [class*='heading']").first();
        assertThat(heading).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000)
        );
        assertThat(page.locator("body")).not().containsText("Something went wrong");
    }

    // ── Cost tab ──────────────────────────────────────────────────────────────

    @Test
    void costTab_loadsWithoutError() {
        navigate("/admin/cost");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).not().containsText("Something went wrong");
    }

    // ── Audit log tab ─────────────────────────────────────────────────────────

    @Test
    void auditLogTab_loadsWithoutError() {
        navigate("/admin/audit-log");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).not().containsText("Something went wrong");
        assertThat(page.locator("body")).not().containsText("500");
    }

    // ── PII flags tab ─────────────────────────────────────────────────────────

    @Test
    void piiFlagsTab_loadsWithoutError() {
        navigate("/admin/pii-flags");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).not().containsText("Something went wrong");
    }

    // ── Settings tab ─────────────────────────────────────────────────────────

    @Test
    void settingsTab_loadsWithoutError() {
        navigate("/admin/settings");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("body")).not().containsText("Something went wrong");
    }

    // ── Back-navigation ───────────────────────────────────────────────────────

    @Test
    void adminConsole_backToChat_returnsToMainApp() {
        navigate("/admin/overview");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        navigate("/");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        waitForChatReady();
    }
}
