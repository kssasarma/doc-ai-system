# Docs-inator Slack Bot

Query your product documentation directly from Slack.

## Setup

### 1. Create a Slack App

1. Go to https://api.slack.com/apps → **Create New App** → **From scratch**
2. Give it a name (e.g. "Docs-inator") and pick your workspace

### 2. Configure Bot Permissions

In **OAuth & Permissions → Bot Token Scopes**, add:
- `app_mentions:read`
- `chat:write`
- `commands`
- `im:history`
- `im:read`
- `im:write`
- `channels:history`

### 3. Create Slash Command

In **Slash Commands**, create `/docs`:
- **Request URL**: `https://your-domain.com/slack/events`
- **Description**: `Ask Docs-inator a question`
- **Usage hint**: `ask <question> | set-product <product> [version]`

### 4. Enable Socket Mode (for development)

In **Socket Mode**, enable it and create an App-Level Token with `connections:write` scope.

### 5. Install to Workspace

In **Install App**, click **Install to Workspace** and copy the **Bot User OAuth Token**.

### 6. Configure Environment

```bash
cp .env.example .env
# Fill in SLACK_BOT_TOKEN, SLACK_SIGNING_SECRET, SLACK_APP_TOKEN
# Set DOCS_API_URL and DOCS_API_KEY (create a key at /api-keys in the frontend)
```

### 7. Run

```bash
npm install
npm start
```

## Usage

| Command | Description |
|---------|-------------|
| `/docs How do I configure LDAP?` | Ask a question |
| `/docs ask What are the system requirements?` | Explicit ask |
| `/docs set-product case360 23.4` | Set default product for this channel |
| `@docs-inator <question>` | Mention in any channel |
| DM the bot | Private queries |

## Deployment

For production, disable Socket Mode and use an HTTPS endpoint. The bot listens on `PORT` (default 3010).
