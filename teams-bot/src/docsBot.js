import { ActivityHandler, CardFactory, MessageFactory } from 'botbuilder';
import { queryDocs } from './docsClient.js';

const APP_URL = process.env.DOCS_APP_URL || '';

// Per-conversation product/version config (in-memory)
const convConfig = new Map();

export class DocsBot extends ActivityHandler {
  constructor() {
    super();

    this.onMessage(async (context, next) => {
      const text = context.activity.text?.trim() ?? '';
      const convId = context.activity.conversation.id;

      // Commands
      if (text.startsWith('set-product ')) {
        const parts = text.slice('set-product '.length).trim().split(/\s+/);
        const product = parts[0];
        const version = parts[1] || null;
        convConfig.set(convId, { product, version });
        await context.sendActivity(
          `✅ Default product for this conversation set to **${product}**${version ? ` v${version}` : ''}.`
        );
      } else if (text === 'help' || text === '?') {
        await context.sendActivity(this.buildHelpCard());
      } else if (text) {
        const { product, version } = convConfig.get(convId) ?? { product: null, version: null };

        await context.sendActivity(MessageFactory.text('_Searching documentation…_'));

        try {
          const result = await queryDocs(text, product, version);
          const card = this.buildAnswerCard(result, text);
          await context.sendActivity(MessageFactory.attachment(card));
        } catch (err) {
          await context.sendActivity(`❌ Error: ${err.message}`);
        }
      }

      await next();
    });

    this.onMembersAdded(async (context, next) => {
      for (const member of context.activity.membersAdded) {
        if (member.id !== context.activity.recipient.id) {
          await context.sendActivity(
            "👋 Hi! I'm **Docs-inator** — ask me anything about your product documentation.\n\n" +
            "Type `help` for available commands, or just ask your question directly."
          );
        }
      }
      await next();
    });
  }

  buildAnswerCard(result, question) {
    const { answer, confidenceLabel, sources = [], chatId, relatedQuestions = [] } = result;
    const confEmoji = confidenceLabel === 'HIGH' ? '🟢' : confidenceLabel === 'MEDIUM' ? '🟡' : '🔴';

    const body = [
      { type: 'TextBlock', text: `**Q:** ${question}`, wrap: true, weight: 'Bolder' },
      { type: 'TextBlock', text: answer, wrap: true },
    ];

    if (sources.length > 0) {
      body.push({
        type: 'TextBlock',
        text: `${confEmoji} **${confidenceLabel}** confidence · Sources: ${
          sources.slice(0, 3).map(s => `_${s.document}${s.version ? ` v${s.version}` : ''}_`).join(', ')
        }`,
        wrap: true,
        isSubtle: true,
        size: 'Small',
      });
    }

    if (relatedQuestions.length > 0) {
      body.push({ type: 'TextBlock', text: '**Related questions:**', wrap: true });
      body.push({
        type: 'TextBlock',
        text: relatedQuestions.map(q => `• ${q}`).join('\n'),
        wrap: true,
        isSubtle: true,
      });
    }

    const actions = [];
    if (APP_URL && chatId) {
      actions.push({
        type: 'Action.OpenUrl',
        title: '💬 Continue conversation',
        url: `${APP_URL}/chat/${chatId}`,
      });
    }

    return CardFactory.adaptiveCard({
      type: 'AdaptiveCard',
      $schema: 'http://adaptivecards.io/schemas/adaptive-card.json',
      version: '1.4',
      body,
      actions,
    });
  }

  buildHelpCard() {
    return MessageFactory.attachment(CardFactory.adaptiveCard({
      type: 'AdaptiveCard',
      $schema: 'http://adaptivecards.io/schemas/adaptive-card.json',
      version: '1.4',
      body: [
        { type: 'TextBlock', text: '**Docs-inator Commands**', size: 'Large', weight: 'Bolder' },
        { type: 'TextBlock', text: '• Ask any question about your documentation', wrap: true },
        { type: 'TextBlock', text: '• `set-product <product> [version]` — set default product for this conversation', wrap: true },
        { type: 'TextBlock', text: '• `help` — show this message', wrap: true },
      ],
    }));
  }
}
