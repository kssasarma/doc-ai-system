export interface ReasoningStep {
  subQuestion: string;
  chunksFound: number;
  maxSimilarity: number;
}

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
  relatedQuestions?: string[];
  isBookmarked?: boolean;
  upvoteCount?: number;
  userUpvoted?: boolean;
  reasoningChain?: ReasoningStep[];
}

export interface ChatSession {
  chatId: string;
  title: string;
  messages: ChatMessage[];
  createdAt: number;
  updatedAt: number;
  pinned?: boolean;
  tags?: string[];
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
  sessionTitle?: string;
  relatedQuestions?: string[];
  reasoningChain?: ReasoningStep[];
}

export interface APIResponse {
  success: boolean;
  data?: BackendChatResponse;
  error?: string;
}

export interface BackendSession {
  chatId: string;
  title?: string;
  createdAt: string;
  lastActiveAt: string;
  messageCount: number;
  product: string | null;
  version: string | null;
  pinned?: boolean;
  tags?: string[];
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
  upvoteCount?: number;
  userUpvoted?: boolean;
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

// Bookmark types
export interface Bookmark {
  id: string;
  chatMessageId: string;
  chatId: string;
  messageExcerpt?: string;
  title?: string;
  note?: string;
  tags?: string[];
  createdAt?: string;
}

// User preference types
export interface UserPreference {
  verbosity: 'CONCISE' | 'BALANCED' | 'DETAILED';
  answerFormat: 'PROSE' | 'BULLET_POINTS' | 'CODE_FIRST';
  defaultProduct?: string;
  defaultVersion?: string;
}

// Phase 3 — Team Collaboration types

export interface ShareLink {
  token: string;
  chatId: string;
  publicAccess: boolean;
  expiresAt?: string;
  createdAt: string;
}

export interface SharedChatMessage {
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string;
}

export interface SharedChatSession {
  token: string;
  chatId: string;
  title?: string;
  product?: string;
  version?: string;
  createdByUsername: string;
  expiresAt?: string;
  messages: SharedChatMessage[];
}

export interface Collection {
  id: string;
  name: string;
  description?: string;
  publicAccess: boolean;
  createdBy: string;
  createdByUsername?: string;
  isOwner: boolean;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CollectionItem {
  id: string;
  collectionId: string;
  chatMessageId: string;
  chatId: string;
  messageContent?: string;
  note?: string;
  addedByUsername?: string;
  createdAt: string;
}

export interface UpvoteStatus {
  chatMessageId: string;
  upvoteCount: number;
  userUpvoted: boolean;
}

export interface Escalation {
  id: string;
  chatMessageId: string;
  questionText: string;
  aiAnswerText?: string;
  status: 'PENDING' | 'IN_REVIEW' | 'ANSWERED' | 'CLOSED';
  createdBy: string;
  createdByUsername?: string;
  assignedTo?: string;
  expertAnswer?: string;
  product?: string;
  version?: string;
  createdAt: string;
  answeredAt?: string;
}

export interface ChunkAnnotation {
  id: string;
  documentChunkId: string;
  userId: string;
  username?: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface AppNotification {
  id: string;
  type: 'ESCALATION_ANSWERED' | 'SHARE_FORKED' | 'COLLECTION_UPDATED' | 'ANNOTATION_ADDED';
  title: string;
  body?: string;
  referenceId?: string;
  read: boolean;
  createdAt: string;
}
