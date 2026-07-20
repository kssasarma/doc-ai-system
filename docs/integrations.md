# Integrations

Docs-inator ships with four optional client integrations. All of them call the [Public API v1](api-reference.md#public-api-v1-api-key-auth--for-integrations) using an API key — they require no changes to the core services.

Create an API key before configuring any integration:

```bash
curl -X POST http://localhost:8082/api/user/api-keys \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name":"slack-bot"}'
# Save the returned "key" value — shown only once
```

---

## Slack Bot

The Slack bot responds to `/docs` slash commands and `@docs-inator` mentions in any channel or DM.

### Create the Slack App

1. Go to https://api.slack.com/apps → **Create New App** → **From scratch**
2. Name it (e.g. "Docs-inator") and select your workspace

### Configure Bot Token Scopes

In **OAuth & Permissions → Bot Token Scopes**, add:

- `app_mentions:read`
- `channels:history`
- `chat:write`
- `commands`
- `im:history`
- `im:read`
- `im:write`

### Create the `/docs` Slash Command

In **Slash Commands**, create `/docs`:

- **Request URL:** `https://your-domain.com/slack/events`
- **Description:** `Ask Docs-inator a question`
- **Usage hint:** `ask <question> | set-product <product> [version]`

### Enable Socket Mode (development)

In **Socket Mode**, enable it and create an App-Level Token with the `connections:write` scope.

### Install to Workspace

In **Install App**, click **Install to Workspace** and copy the **Bot User OAuth Token** (`xoxb-...`).

### Start the bot

```bash
cd slack-bot
npm install

SLACK_BOT_TOKEN=xoxb-...       \
SLACK_APP_TOKEN=xapp-...       \
DOCS_AI_URL=http://localhost:8082   \
DOCS_AI_TOKEN=dak_...          \
node src/index.js
# Listening on port 3001
```

| Variable | Description |
|---|---|
| `SLACK_BOT_TOKEN` | Bot User OAuth Token (`xoxb-...`) |
| `SLACK_APP_TOKEN` | App-Level Token for Socket Mode (`xapp-...`) |
| `DOCS_AI_URL` | documentation-bot base URL |
| `DOCS_AI_TOKEN` | API key created above |

### Usage

| Command | Action |
|---|---|
| `/docs ask <question>` | Ask a question; answer posted in channel |
| `/docs <question>` | Shorthand for `ask` |
| `/docs set-product <product> [version]` | Set the default product scope for the channel |
| `@docs-inator <question>` | Mention in any thread; answer appears as a thread reply |
| DM to `@docs-inator` | Private query with no channel scope |

### CORS note

If the Slack bot is hosted on a different domain from your frontend, add its origin to `CORS_ALLOWED_ORIGINS` in the bot service.

---

## Microsoft Teams Bot

The Teams bot is equivalent to the Slack bot, built on the Bot Framework SDK.

### Register an Azure Bot

1. In the [Azure Portal](https://portal.azure.com), create a new **Azure Bot** resource.
2. Note the **Application ID** and generate a **Client Secret** under the app registration.
3. Set the messaging endpoint to `https://your-domain.com/api/messages`.

### Start the bot

```bash
cd teams-bot
npm install

MicrosoftAppId=<azure-app-id>            \
MicrosoftAppPassword=<azure-app-secret>  \
DOCS_AI_URL=http://localhost:8082        \
DOCS_AI_TOKEN=dak_...                   \
node src/index.js
# Listening on port 3002
```

| Variable | Description |
|---|---|
| `MicrosoftAppId` | Azure Bot Application ID |
| `MicrosoftAppPassword` | Azure Bot Client Secret |
| `DOCS_AI_URL` | documentation-bot base URL |
| `DOCS_AI_TOKEN` | API key created above |

### Usage

Mention `@Docs-inator` in any Teams channel or chat to ask a question. The bot responds in-thread with the answer and source citations.

---

## Chrome / Edge Browser Extension

A Manifest V3 extension that adds a popup chat interface and a context-menu action to any page.

### Install (development)

1. Open `chrome://extensions/` (or `edge://extensions/`)
2. Enable **Developer mode** (toggle in the top right)
3. Click **Load unpacked** and select the `browser-extension/` directory

### Configure

On first launch, click the extension icon and go to **Settings**:

- **Backend URL:** The public URL of documentation-bot (e.g. `https://api.your-domain.com`)
- **API Key:** The `dak_...` key created above
- **Default product/version:** Optional; pre-fills the scope for every query

### CORS setup (required)

The browser extension's origin must be allowed by the bot service. Find your extension ID in `chrome://extensions/` and add it to `CORS_ALLOWED_ORIGINS`:

```dotenv
CORS_ALLOWED_ORIGINS=https://app.your-domain.com,chrome-extension://<your-extension-id>
```

The extension also requires a runtime permission grant for the backend origin. On first API call, the browser will prompt the user to allow it.

### Usage

| Action | How to trigger |
|---|---|
| Popup chat | Click the Docs-inator icon in the browser toolbar (`Ctrl+Shift+D`) |
| Context-menu query | Highlight text on any page → right-click → **Ask Docs-inator** |
| Answer with citations | Results appear in the popup with clickable source links |

---

## VS Code Extension

A sidebar panel that lets developers query documentation without leaving their editor.

### Install (development)

```bash
cd vscode-extension
npm install
```

Open the `vscode-extension` folder in VS Code and press **F5** to launch the Extension Development Host.

### Configure

Open the command palette (`Ctrl+Shift+P`) and run **Docs-inator: Configure**:

- **Backend URL:** documentation-bot URL
- **API Key:** The `dak_...` key created above

Or add a `.vscode/docs-inator.json` file to the workspace:

```json
{
  "backendUrl": "https://api.your-domain.com",
  "product": "MyProduct",
  "version": "2.1.0"
}
```

### Usage

| Action | How to trigger |
|---|---|
| Open sidebar | Click the Docs-inator icon in the Activity Bar |
| Ask a question | Type in the sidebar search box |
| Search for a symbol | Right-click a symbol in the editor → **Search Docs-inator for this** |
| Answers | Rendered in the sidebar panel with source links |

The workspace `.vscode/docs-inator.json` sets the default product and version, so queries are pre-scoped to the relevant documentation without manual selection.

---

## Docker Compose — Enabling Bots

The Slack and Teams bots are in the `bots` Docker Compose profile (disabled by default). Enable them:

```bash
docker compose --profile bots up -d
```

Set `SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`, `MicrosoftAppId`, `MicrosoftAppPassword`, and `DOCS_AI_TOKEN` in your `.env` file before starting with the profile.
