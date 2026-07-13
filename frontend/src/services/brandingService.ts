import axios from 'axios';
import { BACKEND_URL } from '../config/backend';

const BOT_URL = BACKEND_URL;

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

/** Public pre-login (no token) returns the platform default; once the caller is authenticated,
 * passing their token lets the backend resolve their own tenant's branding instead (see
 * TenantResolutionFilter — it trusts the JWT's tenant claim, never a client-supplied header). */
export async function fetchBranding(token?: string | null): Promise<TenantBranding> {
  const { data } = await axios.get<TenantBranding>(`${BOT_URL}/api/branding`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
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
