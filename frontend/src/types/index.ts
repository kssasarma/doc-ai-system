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
export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'USER';

export interface AuthUser {
  userId: string;
  username: string;
  email: string;
  role: Role;
  tenantId: string | null;
  mustChangePassword: boolean;
}

export interface AuthResponse {
  token?: string;
  userId?: string;
  username?: string;
  email?: string;
  role?: string;
  mustChangePassword?: boolean;
  error?: string;
}

// Multi-tenant membership — one identity can belong to more than one tenant; exactly one is
// "active" at a time (the tenant/role carried in the current JWT).
export interface TenantMembership {
  tenantId: string;
  tenantName: string;
  role: Role;
  joinedAt: string;
}

// Tenant types
export interface Tenant {
  id: string;
  name: string;
  slug: string;
  plan: string;
  active: boolean;
  maxUsers: number;
  maxDocuments: number;
  oidcEnabled: boolean;
  oidcProvider: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TenantUser {
  userId: string;
  username: string;
  email: string;
  role: Role;
}

// Invitation types
export interface Invitation {
  id: string;
  email: string;
  role: string;
  expiresAt: string;
}

// Per-document access grant types
export interface DocumentGrantee {
  grantId: string;
  userId: string;
  username: string;
  grantedBy: string;
  grantedAt: string;
}

// Group types (Phase 8 — bulk access grants)
export interface Group {
  id: string;
  name: string;
  memberCount: number;
  createdAt: string;
}

export interface GroupMember {
  userId: string;
  username: string;
  email: string;
}

export interface DocumentGroupGrantee {
  grantId: string;
  groupId: string;
  groupName: string;
  grantedBy: string;
  grantedAt: string;
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
}

// Product/version catalog (Phase 7 — backs the chat scope chip)
export interface ProductEntry {
  product: string;
  versions: string[];
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
