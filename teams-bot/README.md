# Docs-inator Teams Bot

Microsoft Teams bot for querying your documentation directly from Teams conversations using Adaptive Cards.

## Prerequisites

- Node.js 18+
- An Azure Bot registration (free tier is sufficient)
- Docs-inator backend running with a valid API key

## Setup

### 1. Create Azure Bot Registration

1. Go to [Azure Portal](https://portal.azure.com) → **Create a resource** → search "Azure Bot"
2. Fill in: Bot handle, Subscription, Resource group, Pricing tier (F0 is free)
3. Set **Type of App** to `Multi Tenant`
4. After creation, go to **Configuration** and note the **Microsoft App ID**
5. Create a client secret: **Configuration** → **Manage Password** → **New client secret**
6. Save `App ID` and `Secret value` — you need both in `.env`

### 2. Configure the Messaging Endpoint

After starting the bot (or using ngrok for local dev):

1. In your Azure Bot → **Configuration**
2. Set **Messaging endpoint** to: `https://your-domain.com/api/messages`
3. Save

### 3. Install the Bot in Teams

1. Azure Bot → **Channels** → add **Microsoft Teams** channel
2. Click "Open in Teams" to test, or package the app for org-wide deployment

### 4. Environment Configuration

```bash
cp .env.example .env
```

| Variable | Description |
|---|---|
| `MICROSOFT_APP_ID` | Azure Bot App ID |
| `MICROSOFT_APP_PASSWORD` | Azure Bot client secret |
| `DOCS_API_URL` | Docs-inator backend URL (e.g. `http://localhost:8082`) |
| `DOCS_API_KEY` | API key from the Docs-inator Settings → API Keys page |
| `DOCS_APP_URL` | Public URL of the Docs-inator frontend (for "Continue conversation" buttons) |
| `PORT` | HTTP port (default `3020`) |

### 5. Run

```bash
npm install
npm start         # production
npm run dev       # development (nodemon auto-reload)
```

### Local Development with ngrok

```bash
ngrok http 3020
# Set the ngrok HTTPS URL as your Azure Bot messaging endpoint
```

## Usage

In any Teams conversation with the bot:

| Message | Action |
|---|---|
| Any question | Queries docs, replies with Adaptive Card answer |
| `set-product <product> [version]` | Sets default product filter for this conversation |
| `help` or `?` | Shows available commands |

The bot responds with Adaptive Cards showing:
- The answer with confidence indicator (🟢 HIGH / 🟡 MEDIUM / 🔴 LOW)
- Source document citations
- Related questions
- "Continue conversation" button linking to the web app

## Deployment

Deploy as a standard Node.js app — Docker, Azure App Service, or any container platform:

```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY src ./src
EXPOSE 3020
CMD ["node", "src/index.js"]
```

The bot requires outbound HTTPS access to `https://smba.trafficmanager.net` (Bot Framework) and your `DOCS_API_URL`.
