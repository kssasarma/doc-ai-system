import axios from 'axios';
import { BACKEND_URL } from '../config/backend';
import type { Tenant, TenantUser, PageResponse } from '../types';

const BOT_URL = BACKEND_URL;

export type { Tenant };

export interface TenantLLMConfig {
  chatProvider: string;
  chatModel: string;
  embeddingProvider: string;
  embeddingModel: string;
  routingEnabled: boolean;
  simpleModel: string;
  complexModel: string;
  azureEndpoint?: string | null;
  azureDeployment?: string | null;
  /** Whether the tenant has a custom (encrypted-at-rest) API key configured — the key itself is
   * write-only and never returned by the API. */
  hasCustomKey: boolean;
  /** Last 4 characters of the configured key (e.g. "••••ab12"), for confirmation only. */
  keyHint?: string | null;
}

export interface TenantLLMConfigUpdate {
  chatProvider: string;
  chatModel: string;
  embeddingProvider: string;
  embeddingModel: string;
  routingEnabled: boolean;
  simpleModel: string;
  complexModel: string;
  azureEndpoint?: string | null;
  azureDeployment?: string | null;
  /** undefined = leave the stored key untouched; "" = clear it; non-empty = set/replace it. */
  apiKey?: string;
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
  token: string, id: string, payload: { provider: string; model: string; apiKey?: string },
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
