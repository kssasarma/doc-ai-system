import 'dotenv/config';
import express from 'express';
import { CloudAdapter, ConfigurationBotFrameworkAuthentication } from 'botbuilder';
import { DocsBot } from './docsBot.js';

const botConfig = {
  MicrosoftAppId: process.env.MICROSOFT_APP_ID,
  MicrosoftAppPassword: process.env.MICROSOFT_APP_PASSWORD,
  MicrosoftAppType: 'MultiTenant',
};

const auth = new ConfigurationBotFrameworkAuthentication(botConfig);
const adapter = new CloudAdapter(auth);
const bot = new DocsBot();

adapter.onTurnError = async (context, error) => {
  console.error('[Teams Bot] Unhandled error:', error);
  await context.sendActivity('An error occurred. Please try again.');
};

const app = express();
app.use(express.json());

app.post('/api/messages', (req, res) => {
  adapter.process(req, res, (context) => bot.run(context));
});

app.get('/health', (_req, res) => res.json({ status: 'ok', service: 'docs-inator-teams-bot' }));

const PORT = process.env.PORT || 3020;
app.listen(PORT, () => {
  console.log(`Teams bot listening on port ${PORT}`);
  console.log(`Messaging endpoint: POST /api/messages`);
});
