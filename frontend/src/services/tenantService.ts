import axios from 'axios';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

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
}

export interface TenantLLMConfig {
  id: string;
  tenantId: string;
  chatProvider: string;
  chatModel: string;
  embeddingProvider: string;
  embeddingModel: string;
  routingEnabled: boolean;
  simpleModel: string;
  complexModel: string;
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
  payload: { name: string; slug: string; plan: string; maxUsers: number; maxDocuments: number },
): Promise<Tenant> {
  const { data } = await axios.post<Tenant>(`${BOT_URL}/api/admin/tenants`, payload, { headers: headers(token) });
  return data;
}

export async function updateTenant(token: string, id: string, payload: Partial<Tenant>): Promise<Tenant> {
  const { data } = await axios.put<Tenant>(`${BOT_URL}/api/admin/tenants/${id}`, payload, { headers: headers(token) });
  return data;
}

export async function getTenantLLMConfig(token: string, id: string): Promise<TenantLLMConfig> {
  const { data } = await axios.get<TenantLLMConfig>(`${BOT_URL}/api/admin/tenants/${id}/llm-config`, { headers: headers(token) });
  return data;
}

export async function updateTenantLLMConfig(token: string, id: string, config: Partial<TenantLLMConfig>): Promise<TenantLLMConfig> {
  const { data } = await axios.put<TenantLLMConfig>(`${BOT_URL}/api/admin/tenants/${id}/llm-config`, config, { headers: headers(token) });
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
