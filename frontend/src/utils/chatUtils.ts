import { ChatSession, ChatMessage } from '../types';

export function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

export function createNewSession(chatId?: string): ChatSession {
  return {
    chatId: chatId || generateId(), // Use backend chatId when available
    title: 'New Chat',
    messages: [],
    messageCount: 0,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    isPersisted: !!chatId,
  };
}

export function createMessage(
  content: string,
  role: 'user' | 'assistant',
  extras?: Pick<ChatMessage, 'messageId' | 'sources' | 'confidence' | 'relatedQuestions' | 'reasoningChain'>
): ChatMessage {
  return {
    id: generateId(),
    content,
    role,
    timestamp: Date.now(),
    ...extras,
  };
}

export function formatTimestamp(timestamp: number): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diffInHours = (now.getTime() - date.getTime()) / (1000 * 60 * 60);

  if (diffInHours < 1) {
    const minutes = Math.floor(diffInHours * 60);
    return `${minutes}m ago`;
  } else if (diffInHours < 24) {
    return `${Math.floor(diffInHours)}h ago`;
  } else {
    return date.toLocaleDateString();
  }
}

export function truncateText(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  return text.slice(0, maxLength) + '...';
}

/**
 * While a streamed answer is mid-fence (the opening ``` has arrived but not the closing one yet),
 * the text isn't valid Markdown for a code block — react-markdown renders it as loose inline
 * content instead, which picks up inline-code styling (colored, no copy button) and visibly
 * flickers once the real closing fence lands and it snaps into a proper `<pre>` block. Closing
 * the fence early (removed again the instant the real one arrives) renders it as a code block
 * from the first line onward instead.
 */
export function closeUnterminatedCodeFence(content: string): string {
  const fenceCount = (content.match(/```/g) ?? []).length;
  return fenceCount % 2 === 1 ? `${content}\n\`\`\`` : content;
}