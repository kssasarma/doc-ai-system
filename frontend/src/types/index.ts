export interface ChatMessage {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  timestamp: number;
  isTyping?: boolean;
  messageId?: string;
  sources?: Source[];
  confidence?: number;
  userFeedback?: 1 | -1 | null;
}

export interface ChatSession {
  chatId: string;
  title: string;
  messages: ChatMessage[];
  createdAt: number;
  updatedAt: number;
}

export interface AppConfig {
  app: {
    title: string;
    subtitle: string;
    description: string;
    version: string;
    logo: string;
  };
  api: {
    endpoint: string;
    timeout: number;
  };
  ui: {
    theme: {
      primary: string;
      secondary: string;
      success: string;
      warning: string;
      error: string;
      background: string;
      surface: string;
      text: string;
    };
    sidebar: {
      width: string;
      collapsedWidth: string;
    };
    chat: {
      maxMessageLength: number;
      typingSpeed: number;
    };
  };
  storage: {
    keys: {
      chatSessions: string;
      activeSession: string;
    };
  };
}

export interface Source {
  chunkId: string;
  document: string;
  relevanceScore?: number;
  product?: string;
  version?: string;
  excerpt?: string;
}

export interface BackendChatResponse {
  answer: string;
  chatId: string;
  messageId: string;
  sources: Source[];
  confidence: number;
}

export interface APIResponse {
  success: boolean;
  data?: BackendChatResponse;
  error?: string;
}

export interface BackendSession {
  chatId: string;
  createdAt: string;
  lastActiveAt: string;
  messageCount: number;
  product: string | null;
  version: string | null;
}

export interface BackendSessionsResponse {
  sessions: BackendSession[];
  totalChats: number;
}

export interface SessionsAPIResponse {
  success: boolean;
  data?: BackendSessionsResponse;
  error?: string;
}

export interface BackendHistoryMessage {
  id: string;
  content: string;
  role: 'USER' | 'ASSISTANT';
  createdAt: string;
}

export interface BackendChatHistoryResponse {
  chatId: string;
  messageCount: number;
  messages: BackendHistoryMessage[];
}

export interface ChatHistoryAPIResponse {
  success: boolean;
  data?: BackendChatHistoryResponse;
  error?: string;
}

// Auth types
export interface AuthUser {
  userId: string;
  username: string;
  email: string;
  role: 'ADMIN' | 'USER';
}

export interface AuthResponse {
  token?: string;
  userId?: string;
  username?: string;
  email?: string;
  role?: string;
  error?: string;
}

// Admin / Document types
export interface DocumentInfo {
  id: string;
  product: string;
  version: string;
  documentName: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  chunkCount?: number;
  errorMessage?: string;
  createdAt?: string;
  message?: string;
  error?: string;
}

export interface IngestionStatus {
  totalDocuments: number;
  completed: number;
  processing: number;
  failed: number;
  pending: number;
  totalChunks: number;
}
