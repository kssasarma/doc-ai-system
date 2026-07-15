# Docs-inator Browser Extension

Chrome and Edge browser extension (Manifest V3) for querying your documentation without leaving your current tab.

## Features

- **Popup chat** — ask questions directly from the toolbar icon
- **Context menu** — right-click selected text → "Ask Docs-inator: …"
- **Keyboard shortcut** — `Ctrl+Shift+D` (Mac: `Cmd+Shift+D`) opens the popup
- **Conversation continuity** — each popup session has a chat ID; "Open in app" link continues the conversation in the full web UI

## Installation

### From source (Developer mode)

1. Clone this repository
2. Open Chrome/Edge → `chrome://extensions` / `edge://extensions`
3. Enable **Developer mode** (top right)
4. Click **Load unpacked** → select the `browser-extension/` folder
5. The Docs-inator icon appears in the toolbar

### First-time setup

Click the extension icon → fill in:

| Field | Value |
|---|---|
| **API URL** | Your Docs-inator backend URL, e.g. `https://docs.example.com` |
| **API Key** | An API key from Settings → API Keys in the Docs-inator web app |
| **Default Product** | (optional) Filters results to a specific product |
| **Default Version** | (optional) Filters to a specific version |

Click **Save & Connect**.

## Usage

| Action | How |
|---|---|
| Ask a question | Open popup → type → Enter or ➤ |
| Search selected text | Select text on any page → right-click → **Ask Docs-inator** |
| Open keyboard shortcut | `Ctrl+Shift+D` |
| Change settings | Click ⚙️ in the popup header |
| Continue in web app | After an answer, click **Open in app ↗** |

## Permissions

| Permission | Reason |
|---|---|
| `contextMenus` | Right-click "Ask Docs-inator" menu item |
| `storage` | Save API URL, key, and settings locally |
| `activeTab` | (unused at runtime, required for future clipboard features) |

The extension does **not** request `<all_urls>` host permissions upfront — `manifest.json`'s
`host_permissions` is empty. Instead, when you save your backend URL in the setup form, the
extension requests permission for just that one origin via Chrome's `optional_host_permissions`
API (you'll see a one-time browser prompt). If you decline, the popup shows an error and won't
save the URL, since without that grant `fetch()` calls to your backend are subject to the same
CORS restrictions as a request from any other webpage.

### CORS: allow the extension's origin

A Manifest V3 extension's fetches come from an origin like `chrome-extension://<extension-id>`.
The host-permission grant above lets the *browser* allow the request, but your Docs-inator
backend still needs to allow it too — add that origin to `CORS_ALLOWED_ORIGINS` on
documentation-bot (see the root README's Configuration Reference), e.g.:

```
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com,chrome-extension://abcdefghijklmnopqrstuvwxyz123456
```

You'll find your installed extension's id on `chrome://extensions` (enable Developer mode).

## Building for production

No build step required — this is a plain JavaScript Manifest V3 extension. To publish:

1. Update `version` in `manifest.json`
2. Zip the `browser-extension/` folder contents (not the folder itself)
3. Upload to Chrome Web Store or Edge Add-ons
