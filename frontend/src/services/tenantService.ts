import axios from 'axios';
import { BACKEND_URL } from '../config/backend';
import type { Tenant, TenantUser, PageResponse } from '../types';

const BOT_URL = BACKEND_URL;

export type { Tenant };

/**
 * A tenant's complete AI configuration — every AI-related setting (providers, models, endpoints,
 * keys, generation options) lives here, per tenant. There is no platform-level fallback key or
 * endpoint: AI features stay disabled for the tenant until an admin saves a working chat key
 * (and an embedding key, when the embedding provider differs from the chat provider).
 */
export interface TenantLLMConfig {
  chatProvider: string;
  chatModel: string;
  /** Chat endpoint override — empty/null uses the provider's canonical public endpoint. */
  chatBaseUrl?: string | null;
  embeddingProvider: string;
  embeddingModel: string;
  /** Embedding endpoint override — same semantics as chatBaseUrl. */
  embeddingBaseUrl?: string | null;
  routingEnabled: boolean;
  simpleModel: string;
  complexModel: string;
  azureEndpoint?: string | null;
  azureDeployment?: string | null;
  /** Whether a chat API key is stored (encrypted at rest) — the key itself is write-only and
   * never returned by the API. Without one, all AI features are disabled for this tenant. */
  hasChatKey: boolean;
  /** Last 4 characters of the stored chat key (e.g. "••••ab12"), for confirmation only. */
  chatKeyHint?: string | null;
  /** Whether a dedicated embedding key is stored. When absent, the chat key is reused — but only
   * if the embedding provider equals the chat provider. */
  hasEmbeddingKey: boolean;
  embeddingKeyHint?: string | null;
  /** Chat sampling temperature (0–2). */
  temperature: number;
  /** Max completion tokens per chat call. */
  maxTokens: number;
  /** Override for the ingestor's embedding batch token ceiling — null/undefined uses the
   * built-in default. Set it to match the configured embedding model's context window. */
  maxEmbeddingBatchTokens?: number | null;
}

export interface TenantLLMConfigUpdate {
  chatProvider: string;
  chatModel: string;
  chatBaseUrl?: string | null;
  embeddingProvider: string;
  embeddingModel: string;
  embeddingBaseUrl?: string | null;
  routingEnabled: boolean;
  simpleModel: string;
  complexModel: string;
  azureEndpoint?: string | null;
  azureDeployment?: string | null;
  /** undefined = leave the stored key untouched; "" = clear it; non-empty = set/replace it. */
  apiKey?: string;
  /** Same tri-state semantics as apiKey, for the dedicated embedding key. */
  embeddingApiKey?: string;
  temperature?: number | null;
  maxTokens?: number | null;
  /** null = use the built-in default; a positive integer = override for this tenant. */
  maxEmbeddingBatchTokens?: number | null;
}

export interface TestConnectionResult {
  success: boolean;
  message: string;
}

export interface DataRetentionPolicy {
  tenantId: string;
  queryLogDays: number;
  chatSessionDays: number;
  auditLogDays: number;
  feedbackDays: number;
}

function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function listTenants(token: string): Promise<Tenant[]> {
  const { data } = await axios.get<Tenant[]>(`${BOT_URL}/api/admin/tenants`, { headers: headers(token) });
  return data;
}

export async function createTenant(
  token: string,
  payload: { name: string; slug: string; plan: string; maxUsers: number; maxDocuments: number; adminEmail: string },
): Promise<Tenant> {
  const { data } = await axios.post<Tenant>(`${BOT_URL}/api/admin/tenants`, payload, { headers: headers(token) });
  return data;
}

export async function getTenantUsers(
  token: string, id: string, opts?: { q?: string; page?: number; size?: number },
): Promise<PageResponse<TenantUser>> {
  const { data } = await axios.get<PageResponse<TenantUser>>(`${BOT_URL}/api/admin/tenants/${id}/users`, {
    headers: headers(token),
    params: { q: opts?.q || undefined, page: opts?.page ?? 0, size: opts?.size ?? 20 },
  });
  return data;
}

export async function changeUserRole(token: string, userId: string, role: 'ADMIN' | 'USER'): Promise<void> {
  await axios.patch(`${BOT_URL}/api/admin/users/${userId}/role`, { role }, { headers: headers(token) });
}

export async function deactivateUser(token: string, userId: string): Promise<void> {
  await axios.post(`${BOT_URL}/api/admin/users/${userId}/deactivate`, null, { headers: headers(token) });
}

export async function reactivateUser(token: string, userId: string): Promise<void> {
  await axios.post(`${BOT_URL}/api/admin/users/${userId}/reactivate`, null, { headers: headers(token) });
}

export async function eraseUser(token: string, userId: string): Promise<void> {
  await axios.delete(`${BOT_URL}/api/admin/users/${userId}`, { headers: headers(token) });
}

export async function updateTenant(token: string, id: string, payload: Partial<Tenant>): Promise<Tenant> {
  const { data } = await axios.put<Tenant>(`${BOT_URL}/api/admin/tenants/${id}`, payload, { headers: headers(token) });
  return data;
}

export async function getTenantLLMConfig(token: string, id: string): Promise<TenantLLMConfig> {
  const { data } = await axios.get<TenantLLMConfig>(`${BOT_URL}/api/admin/tenants/${id}/llm-config`, { headers: headers(token) });
  return data;
}

export async function updateTenantLLMConfig(token: string, id: string, config: TenantLLMConfigUpdate): Promise<TenantLLMConfig> {
  const { data } = await axios.put<TenantLLMConfig>(`${BOT_URL}/api/admin/tenants/${id}/llm-config`, config, { headers: headers(token) });
  return data;
}

export async function testTenantLLMConnection(
  token: string, id: string, payload: { provider: string; model: string; apiKey?: string; baseUrl?: string },
): Promise<TestConnectionResult> {
  const { data } = await axios.post<TestConnectionResult>(
    `${BOT_URL}/api/admin/tenants/${id}/llm-config/test`, payload, { headers: headers(token) });
  return data;
}

export async function getRetentionPolicy(token: string, id: string): Promise<DataRetentionPolicy> {
  const { data } = await axios.get<DataRetentionPolicy>(`${BOT_URL}/api/admin/tenants/${id}/retention`, { headers: headers(token) });
  return data;
}

export async function updateRetentionPolicy(token: string, id: string, policy: Partial<DataRetentionPolicy>): Promise<DataRetentionPolicy> {
  const { data } = await axios.put<DataRetentionPolicy>(`${BOT_URL}/api/admin/tenants/${id}/retention`, policy, { headers: headers(token) });
  return data;
}
