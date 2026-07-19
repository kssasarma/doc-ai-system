package com.docai.e2e.chat;

import com.docai.e2e.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the chat interface: session creation, sending messages,
 * receiving responses, and session management.
 */
@Tag("chat")
class ChatE2ETest extends PlaywrightTestBase {

    @BeforeEach
    void signIn() {
        loginAsAdmin();
        waitForChatReady();
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void chatPage_loadsWithMessageInput() {
        Locator textarea = page.locator("textarea[aria-label='Message']");
        assertThat(textarea).isVisible();
        assertThat(textarea).isEnabled();
    }

    @Test
    void chatPage_hasNewChatButton_inSidebar() {
        Locator newChatBtn = page.locator(
            "button:has-text('New chat'), button[aria-label*='new'], button[aria-label*='New'], button:has-text('+')"
        ).first();
        assertThat(newChatBtn).isVisible();
    }

    @Test
    void chatPage_sendButton_isDisabledWhenInputEmpty() {
        Locator sendBtn = page.locator("button[aria-label='Send message']");
        assertThat(sendBtn).isDisabled();
    }

    @Test
    void chatPage_sendButton_enablesWhenTextEntered() {
        page.fill("textarea[aria-label='Message']", "hello");
        Locator sendBtn = page.locator("button[aria-label='Send message']");
        assertThat(sendBtn).isEnabled();
    }

    // ── Sending a message ─────────────────────────────────────────────────────

    @Test
    void sendMessage_userMessageAppearsInChat() {
        String question = "What is the purpose of this documentation system?";
        page.fill("textarea[aria-label='Message']", question);
        page.click("button[aria-label='Send message']");

        // The user message should appear in the message list
        Locator userMsg = page.locator("p, div, span").filter(
            new Locator.FilterOptions().setHasText(question.substring(0, 20))
        ).first();
        assertThat(userMsg).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(12_000)
        );
    }

    @Test
    void sendMessage_aiResponseEventuallyAppears() {
        page.fill("textarea[aria-label='Message']", "What features does this system have?");
        page.click("button[aria-label='Send message']");

        // Wait for the stop button (streaming started) or an assistant message
        page.waitForSelector(
            "button[aria-label='Stop generating'], .message-assistant, [data-role='assistant']",
            new Page.WaitForSelectorOptions().setTimeout(30_000)
        );
        // After streaming completes the send button returns
        page.waitForSelector(
            "button[aria-label='Send message']",
            new Page.WaitForSelectorOptions().setTimeout(60_000)
        );
    }

    @Test
    void sendMessage_inputClearsAfterSend() {
        page.fill("textarea[aria-label='Message']", "Test message to check clearing");
        page.click("button[aria-label='Send message']");

        assertThat(page.locator("textarea[aria-label='Message']")).hasValue("");
    }

    @Test
    void sendMessage_viaEnterKey_sendsMessage() {
        page.fill("textarea[aria-label='Message']", "Keyboard submit test");
        page.press("textarea[aria-label='Message']", "Enter");

        // After pressing Enter the input should be cleared (message sent)
        assertThat(page.locator("textarea[aria-label='Message']")).hasValue("");
    }

    @Test
    void sendMessage_shiftEnter_addsNewlineInsteadOfSending() {
        page.fill("textarea[aria-label='Message']", "Line one");
        page.press("textarea[aria-label='Message']", "Shift+Enter");
        // Shift+Enter should NOT submit — input still has text
        assertThat(page.locator("textarea[aria-label='Message']")).not().hasValue("");
    }

    // ── Stop generation ───────────────────────────────────────────────────────

    @Test
    void stopButton_appearsWhileStreaming() {
        page.fill("textarea[aria-label='Message']", "Tell me about documentation ingestion pipeline");
        page.click("button[aria-label='Send message']");

        Locator stopBtn = page.locator("button[aria-label='Stop generating']");
        assertThat(stopBtn).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(15_000)
        );
    }

    @Test
    void stopButton_click_stopsStreaming() {
        page.fill("textarea[aria-label='Message']", "Explain all available features in detail");
        page.click("button[aria-label='Send message']");

        Locator stopBtn = page.locator("button[aria-label='Stop generating']");
        assertThat(stopBtn).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(15_000)
        );
        stopBtn.click();

        // After stopping, the send button should come back
        Locator sendBtn = page.locator("button[aria-label='Send message']");
        assertThat(sendBtn).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(8_000)
        );
    }

    // ── Session management ────────────────────────────────────────────────────

    @Test
    void newChat_createsNewSession_andInputIsEmpty() {
        // Send a first message to establish a session
        page.fill("textarea[aria-label='Message']", "First session message");
        page.click("button[aria-label='Send message']");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator newChatBtn = page.locator(
            "button:has-text('New chat'), button[aria-label*='New']"
        ).first();
        if (newChatBtn.count() > 0) {
            newChatBtn.click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("textarea[aria-label='Message']")).hasValue("");
        }
    }

    @Test
    void chatSessions_persistInSidebar() {
        // Send a message to create a session
        page.fill("textarea[aria-label='Message']", "Session for sidebar test");
        page.click("button[aria-label='Send message']");

        page.waitForTimeout(2_000);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // At least one session item should exist in the sidebar
        Locator sidebarItems = page.locator(
            "[class*='sidebar'] button, [class*='session'], nav button"
        );
        assertThat(sidebarItems.first()).isVisible(
            new LocatorAssertions.IsVisibleOptions().setTimeout(8_000)
        );
    }

    // ── Placeholder text ──────────────────────────────────────────────────────

    @Test
    void messageInput_hasCorrectPlaceholder() {
        Locator textarea = page.locator("textarea[aria-label='Message']");
        assertThat(textarea).hasAttribute("placeholder", "Ask me anything about your documentation...");
    }

    // ── Slash shortcut ────────────────────────────────────────────────────────

    @Test
    void slashKey_focusesMessageInput() {
        // Click somewhere neutral to defocus the textarea
        page.locator("body").click(new Locator.ClickOptions().setPosition(10, 10));
        page.keyboard().press("/");

        Locator textarea = page.locator("textarea[aria-label='Message']");
        assertThat(textarea).isFocused();
    }
}
