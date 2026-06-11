/**
 * Format a Docs-inator API response into Slack Block Kit blocks.
 */
export function formatAnswerBlocks(result, question, appUrl) {
  const { answer, confidence, confidenceLabel, sources = [], relatedQuestions = [], chatId } = result;

  const confColor = confidenceLabel === 'HIGH' ? '🟢'
    : confidenceLabel === 'MEDIUM' ? '🟡' : '🔴';

  const blocks = [
    // Question echo
    {
      type: 'section',
      text: { type: 'mrkdwn', text: `*Question:* ${escapeMarkdown(question)}` },
    },
    { type: 'divider' },
    // Answer
    {
      type: 'section',
      text: { type: 'mrkdwn', text: answer },
    },
  ];

  // Confidence + sources footer
  const footerParts = [`${confColor} *${confidenceLabel}* confidence`];
  if (sources.length > 0) {
    const sourceList = sources
      .slice(0, 3)
      .map(s => `_${s.document}${s.version ? ` v${s.version}` : ''}_`)
      .join(' · ');
    footerParts.push(`Sources: ${sourceList}`);
  }
  blocks.push({
    type: 'context',
    elements: [{ type: 'mrkdwn', text: footerParts.join('  |  ') }],
  });

  // "Continue in Docs-inator" link
  if (appUrl && chatId) {
    blocks.push({
      type: 'actions',
      elements: [
        {
          type: 'button',
          text: { type: 'plain_text', text: '💬 Continue conversation →' },
          url: `${appUrl}/chat/${chatId}`,
          action_id: 'open_chat',
        },
      ],
    });
  }

  // Related questions
  if (relatedQuestions.length > 0) {
    blocks.push({ type: 'divider' });
    blocks.push({
      type: 'section',
      text: {
        type: 'mrkdwn',
        text: `*Related questions:*\n${relatedQuestions.map(q => `• ${q}`).join('\n')}`,
      },
    });
  }

  return blocks;
}

function escapeMarkdown(text) {
  return text.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
}
