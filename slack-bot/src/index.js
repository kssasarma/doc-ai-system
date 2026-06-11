import 'dotenv/config';
import { App } from '@slack/bolt';
import { queryDocs } from './docsClient.js';
import { formatAnswerBlocks } from './formatters.js';
import { getChannelConfig, setChannelConfig } from './channelConfig.js';

const APP_URL = process.env.DOCS_APP_URL || '';

const app = new App({
  token: process.env.SLACK_BOT_TOKEN,
  signingSecret: process.env.SLACK_SIGNING_SECRET,
  // Use Socket Mode for development; remove for production HTTP
  socketMode: !!process.env.SLACK_APP_TOKEN,
  appToken: process.env.SLACK_APP_TOKEN,
  port: Number(process.env.PORT) || 3010,
});

// ── /docs slash command ────────────────────────────────────────────────────────

app.command('/docs', async ({ command, ack, respond }) => {
  await ack();

  const text = command.text.trim();
  const channelId = command.channel_id;

  // /docs set-product <product> [version]
  if (text.startsWith('set-product ')) {
    const parts = text.slice('set-product '.length).trim().split(/\s+/);
    const product = parts[0];
    const version = parts[1] || null;
    setChannelConfig(channelId, product, version);
    await respond({
      response_type: 'in_channel',
      text: `✅ Default product for this channel set to *${product}*${version ? ` v${version}` : ' (all versions)'}.`,
    });
    return;
  }

  // /docs ask <question>
  const question = text.startsWith('ask ') ? text.slice('ask '.length) : text;
  if (!question) {
    await respond({
      text: [
        '*Docs-inator commands:*',
        '• `/docs ask <question>` — ask a question about the documentation',
        '• `/docs <question>` — shorthand for ask',
        '• `/docs set-product <product> [version]` — set the default product for this channel',
      ].join('\n'),
    });
    return;
  }

  const { product, version } = getChannelConfig(channelId);

  await respond({ text: `_Searching documentation…_`, response_type: 'ephemeral' });

  try {
    const result = await queryDocs(question, product, version);
    await respond({
      response_type: 'in_channel',
      blocks: formatAnswerBlocks(result, question, APP_URL),
      text: result.answer, // fallback for notifications
    });
  } catch (err) {
    await respond({ text: `❌ Error: ${err.message}` });
  }
});

// ── @docs-inator mentions ──────────────────────────────────────────────────────

app.event('app_mention', async ({ event, say }) => {
  const botId = app.client.auth?.userId;
  const question = event.text
    .replace(/<@[^>]+>/g, '')  // strip @mentions
    .trim();

  if (!question) {
    await say({ text: 'Hi! Mention me with a question, like: `@docs-inator How do I configure LDAP?`' });
    return;
  }

  const { product, version } = getChannelConfig(event.channel);

  // Reply in thread if invoked in a thread
  const replyOptions = event.thread_ts
    ? { thread_ts: event.thread_ts }
    : { thread_ts: event.ts };

  try {
    const result = await queryDocs(question, product, version);
    await say({
      ...replyOptions,
      blocks: formatAnswerBlocks(result, question, APP_URL),
      text: result.answer,
    });
  } catch (err) {
    await say({ ...replyOptions, text: `❌ Error reaching documentation service: ${err.message}` });
  }
});

// ── Direct messages ───────────────────────────────────────────────────────────

app.message(async ({ message, say, payload }) => {
  // Only handle DMs (channel_type im), not bot messages
  if (payload.channel_type !== 'im' || message.bot_id) return;

  const question = message.text?.trim();
  if (!question) return;

  try {
    const result = await queryDocs(question, null, null);
    await say({
      blocks: formatAnswerBlocks(result, question, APP_URL),
      text: result.answer,
    });
  } catch (err) {
    await say({ text: `❌ ${err.message}` });
  }
});

// ── Action handlers ───────────────────────────────────────────────────────────

app.action('open_chat', async ({ ack }) => {
  await ack(); // link_button — just ack, Slack handles the URL
});

// ── Start ─────────────────────────────────────────────────────────────────────

(async () => {
  await app.start();
  console.log(`⚡ Docs-inator Slack bot running (port ${process.env.PORT || 3010})`);
})();
