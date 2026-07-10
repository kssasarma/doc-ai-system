import axios from 'axios';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

export interface TenantBranding {
  productName: string;
  logoUrl: string | null;
  faviconUrl: string | null;
  primaryColor: string;
  accentColor: string;
  customCss: string | null;
  supportEmail: string | null;
  footerText: string | null;
}

export async function fetchBranding(): Promise<TenantBranding> {
  const { data } = await axios.get<TenantBranding>(`${BOT_URL}/api/branding`);
  return data;
}

export async function getTenantBranding(token: string, tenantId: string): Promise<TenantBranding> {
  const { data } = await axios.get<TenantBranding>(
    `${BOT_URL}/api/admin/tenants/${tenantId}/branding`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  return data;
}

export async function updateBranding(
  tenantId: string,
  branding: Partial<TenantBranding>,
  token: string,
): Promise<TenantBranding> {
  const { data } = await axios.put<TenantBranding>(
    `${BOT_URL}/api/admin/tenants/${tenantId}/branding`,
    branding,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  return data;
}
