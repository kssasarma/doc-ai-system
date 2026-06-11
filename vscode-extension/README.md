# Docs-inator VS Code Extension

Query your product documentation without leaving VS Code. Get instant answers in a sidebar panel, or right-click selected text to ask about it directly.

## Features

- **Sidebar chat panel** — persistent conversation with your docs
- **Context menu** — right-click any selected text → "Docs-inator: Ask about selection"
- **Keyboard shortcut** — `Ctrl+Shift+D` (Mac: `Cmd+Shift+D`) to focus the panel
- **Confidence indicators** — 🟢 HIGH / 🟡 MEDIUM / 🔴 LOW on every answer
- **"Open in web app" link** — continue the conversation in the full Docs-inator UI

## Installation

### From VSIX

```bash
npm install
npm run compile
npx vsce package
# Then install: Extensions → ... → Install from VSIX
```

### From source (development)

1. Open this folder in VS Code
2. `npm install && npm run compile`
3. Press `F5` to launch the Extension Development Host

## Configuration

Run **Docs-inator: Configure API connection** from the Command Palette (`Ctrl+Shift+P`), or set these in your `settings.json`:

| Setting | Description |
|---|---|
| `docsinator.apiUrl` | Backend URL, e.g. `https://docs.example.com` |
| `docsinator.apiKey` | API key from the Docs-inator Settings → API Keys page |
| `docsinator.defaultProduct` | (optional) Default product filter |
| `docsinator.defaultVersion` | (optional) Default version filter |

## Usage

| Action | How |
|---|---|
| Open panel | `Ctrl+Shift+D` or click the 📚 icon in the Activity Bar |
| Ask a question | Type in the sidebar input and press Enter |
| Ask about selected code | Select text → right-click → **Docs-inator: Ask about selection** |
| Configure | Command Palette → **Docs-inator: Configure API connection** |
