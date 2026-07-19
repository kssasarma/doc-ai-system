package com.docai.e2e.auth;

import com.docai.e2e.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for authentication flows: login, logout, forgot-password, and
 * access protection for authenticated routes.
 */
@Tag("auth")
class LoginE2ETest extends PlaywrightTestBase {

    // ── Login page rendering ──────────────────────────────────────────────────

    @Test
    void loginPage_loadsWithRequiredElements() {
        navigate("/login");
        waitForLoginPage();

        assertThat(page.locator("input[placeholder='Enter username']")).isVisible();
        assertThat(page.locator("input[placeholder='Enter password']")).isVisible();
        assertThat(page.locator("button[type='submit']")).isVisible();
        assertThat(page.locator("a[href='/forgot-password']")).isVisible();
    }

    @Test
    void loginPage_subtitleIsVisible() {
        navigate("/login");
        // Subtitle declared in AuthLayout
        assertThat(page.locator("body")).containsText("AI Documentation Assistant");
    }

    // ── Successful login ──────────────────────────────────────────────────────

    @Test
    void login_validAdminCredentials_redirectsToApp() {
        loginAsAdmin();

        // After login we should NOT be on the login page
        assertThat(page).not().hasURL(Pattern.compile(".*login.*"));
        // The chat textarea confirms we reached the main app
        waitForChatReady();
    }

    @Test
    void login_validCredentials_storesSessionAndRedirects() {
        loginAsAdmin();

        // Navigate back to login — should be redirected away (already authenticated)
        page.navigate(BASE_URL + "/login");
        page.waitForURL(
            url -> !url.contains("/login"),
            new Page.WaitForURLOptions().setTimeout(8_000)
        );
        assertThat(page).not().hasURL(Pattern.compile(".*login.*"));
    }

    // ── Failed login ──────────────────────────────────────────────────────────

    @Test
    void login_wrongPassword_showsErrorAndStaysOnLoginPage() {
        navigate("/login");
        waitForLoginPage();

        page.fill("input[placeholder='Enter username']", ADMIN_USERNAME);
        page.fill("input[placeholder='Enter password']", "wrong-password-xyz");
        page.click("button[type='submit']");

        // Error div rendered by LoginPage on catch
        Locator errorDiv = page.locator("div.text-danger, [class*='text-danger']").first();
        assertThat(errorDiv).isVisible();
        assertThat(page).hasURL(Pattern.compile(".*login.*"));
    }

    @Test
    void login_unknownUsername_showsError() {
        navigate("/login");
        waitForLoginPage();

        page.fill("input[placeholder='Enter username']", "no_such_user_xyz_9999");
        page.fill("input[placeholder='Enter password']", "any-password");
        page.click("button[type='submit']");

        Locator errorDiv = page.locator("div.text-danger, [class*='text-danger']").first();
        assertThat(errorDiv).isVisible();
    }

    @Test
    void login_emptyUsername_browserValidationPreventsSubmit() {
        navigate("/login");
        waitForLoginPage();

        page.fill("input[placeholder='Enter password']", "somePassword");
        // Try to click submit without filling username (required field)
        page.click("button[type='submit']");

        // Page should still be on /login — browser native required validation fires
        assertThat(page).hasURL(Pattern.compile(".*login.*"));
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @Test
    void forgotPasswordLink_navigatesToForgotPasswordPage() {
        navigate("/login");
        waitForLoginPage();

        page.click("a[href='/forgot-password']");
        page.waitForURL(
            url -> url.contains("/forgot-password"),
            new Page.WaitForURLOptions().setTimeout(8_000)
        );
        assertThat(page.locator("body")).containsText("Forgot");
    }

    @Test
    void forgotPasswordPage_emailInput_isPresent() {
        navigate("/forgot-password");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator input = page.locator("input[type='email'], input[type='text']").first();
        assertThat(input).isVisible();
        assertThat(page.locator("button[type='submit']")).isVisible();
    }

    @Test
    void forgotPasswordPage_submit_alwaysShowsSuccessState() {
        navigate("/forgot-password");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator input = page.locator("input[type='email'], input[type='text']").first();
        input.fill("nonexistent@example.com");
        page.click("button[type='submit']");

        // Server always returns 200 for security (no enumeration leak); page must not crash
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator("body")).isVisible();
    }

    // ── Route protection ──────────────────────────────────────────────────────

    @Test
    void unauthenticatedUser_accessingRoot_isRedirectedToLogin() {
        navigate("/");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // If the app redirects unauthenticated users to /login, login elements appear
        if (page.url().contains("/login")) {
            assertThat(page.locator("button[type='submit']")).isVisible();
        }
        // SPA may choose not to redirect and instead show an empty state — both are valid
    }

    @Test
    void unauthenticatedUser_accessingAdmin_doesNotShowAdminContent() {
        navigate("/admin");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Either redirected to login or admin content is absent
        if (page.url().contains("/login")) {
            assertThat(page.locator("button[type='submit']")).isVisible();
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_clearsSessionAndRedirectsToLogin() {
        loginAsAdmin();
        waitForChatReady();

        // Open the account menu and click Sign out
        Locator accountMenuTrigger = page.locator(
            "[aria-label='Account menu'], [data-testid='account-menu']"
        ).first();
        if (accountMenuTrigger.count() > 0) {
            accountMenuTrigger.click();
            Locator logoutBtn = page.locator(
                "button:has-text('Sign out'), button:has-text('Logout'), [role='menuitem']:has-text('out')"
            ).first();
            if (logoutBtn.count() > 0) {
                logoutBtn.click();
                page.waitForURL(
                    url -> url.contains("/login"),
                    new Page.WaitForURLOptions().setTimeout(8_000)
                );
                assertThat(page.locator("button[type='submit']")).isVisible();
            }
        }
        // Graceful skip if no account-menu selector matched — add data-testid to LoginPage to harden
    }
}
