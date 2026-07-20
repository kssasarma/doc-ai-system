package com.docai.e2e;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Base class for all Playwright E2E tests.
 *
 * <p>Lifecycle: one Playwright + Browser instance is shared across the entire test class
 * (via {@code @BeforeAll/@AfterAll}), while a fresh {@code BrowserContext} and {@code Page}
 * are created per test method so each test starts from a clean, unauthenticated state.
 *
 * <p>Configuration via system properties:
 * <ul>
 *   <li>{@code app.base.url} — the frontend origin (default: {@code http://localhost:3000})</li>
 *   <li>{@code playwright.headless} — {@code true} for CI, {@code false} for local debugging</li>
 *   <li>{@code playwright.slow.mo} — milliseconds to slow each action (0 = off)</li>
 * </ul>
 */
public abstract class PlaywrightTestBase {

    protected static final String BASE_URL =
        System.getProperty("app.base.url", "http://localhost:3000");
    protected static final String BOT_API_URL =
        System.getProperty("bot.api.url", "http://localhost:8082");

    /** Default credentials for admin E2E tests — override via system property or env. */
    protected static final String ADMIN_USERNAME =
        System.getProperty("e2e.admin.username", "admin");
    protected static final String ADMIN_PASSWORD =
        System.getProperty("e2e.admin.password", "Admin123!");

    /** Default credentials for regular user E2E tests. */
    protected static final String USER_USERNAME =
        System.getProperty("e2e.user.username", "testuser");
    protected static final String USER_PASSWORD =
        System.getProperty("e2e.user.password", "User123!");

    private static final boolean HEADLESS =
        Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
    private static final int SLOW_MO =
        Integer.parseInt(System.getProperty("playwright.slow.mo", "0"));

    /** Default action timeout: 10 seconds — generous enough for CI, tight enough to fail fast. */
    protected static final int TIMEOUT_MS = 10_000;

    private static Playwright playwright;
    private static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setSlowMo(SLOW_MO)
        );
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext(new Browser.NewContextOptions()
            .setBaseURL(BASE_URL)
            .setIgnoreHTTPSErrors(true)
        );
        context.setDefaultTimeout(TIMEOUT_MS);
        page = context.newPage();
    }

    @AfterEach
    void closeContextAndPage() {
        if (context != null) context.close();
    }

    // ── Navigation helpers ────────────────────────────────────────────────────

    /** Navigate to a path relative to {@link #BASE_URL} and wait for network idle. */
    protected void navigate(String path) {
        page.navigate(BASE_URL + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /** Navigate to login, fill credentials, submit, and wait for redirect away from /login. */
    protected void loginAs(String username, String password) {
        navigate("/login");
        waitForLoginPage();
        page.fill("input[placeholder='Enter username']", username);
        page.fill("input[placeholder='Enter password']", password);
        page.click("button[type='submit']");
        // Wait until we're no longer on the login page
        page.waitForURL(
            url -> !url.contains("/login"),
            new Page.WaitForURLOptions().setTimeout(15_000)
        );
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    protected void loginAsAdmin() {
        loginAs(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    /** Assert the current page URL contains {@code path}. */
    protected void assertUrlContains(String path) {
        assertThat(page).hasURL(Pattern.compile(".*" + Pattern.quote(path) + ".*"));
    }

    /** Assert the page body contains visible text matching {@code text}. */
    protected void assertPageContainsText(String text) {
        assertThat(page.locator("body")).containsText(text);
    }

    // ── Wait helpers ──────────────────────────────────────────────────────────

    protected void waitForLoginPage() {
        page.waitForSelector("button[type='submit']");
    }

    /** Wait for the chat message textarea to be visible — signals the chat page is ready. */
    protected void waitForChatReady() {
        page.waitForSelector("textarea[aria-label='Message']");
    }

    /** Wait for the admin console to load (nav sidebar present). */
    protected void waitForAdminConsole() {
        page.waitForSelector("nav");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }
}
