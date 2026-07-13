import React, { createContext, useContext, useEffect, useState } from 'react';
import { fetchBranding, type TenantBranding } from '../services/brandingService';
import { useAuth } from './AuthContext';

/** "#2563eb" -> "37 99 235" (space-separated RGB triplet, the format tailwind.config.js's
 * `rgb(var(--color-x) / <alpha-value>)` color tokens expect). Falls back to null (leaving the
 * CSS default in index.css in place) for anything that isn't a valid 6-digit hex color. */
function hexToRgbTriplet(hex: string): string | null {
  const match = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!match) return null;
  const int = parseInt(match[1], 16);
  return `${(int >> 16) & 255} ${(int >> 8) & 255} ${int & 255}`;
}

const DEFAULT_BRANDING: TenantBranding = {
  productName: 'Docs-inator',
  logoUrl: null,
  faviconUrl: null,
  primaryColor: '#2563eb',
  accentColor: '#7c3aed',
  customCss: null,
  supportEmail: null,
  footerText: null,
};

interface BrandingContextValue extends TenantBranding {
  /** Re-fetches branding for the current tenant without waiting for a token change — call after
   * an admin saves new branding in SettingsPage so the rest of the app (favicon, title, CSS
   * custom properties, injected custom CSS) picks up the change immediately. */
  refreshBranding: () => Promise<void>;
}

const BrandingContext = createContext<BrandingContextValue>({ ...DEFAULT_BRANDING, refreshBranding: async () => {} });

export function BrandingProvider({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  const [branding, setBranding] = useState<TenantBranding>(DEFAULT_BRANDING);

  const refresh = React.useCallback(async () => {
    try {
      setBranding(await fetchBranding(token));
    } catch {
      // silent fallback to whatever branding is already applied
    }
  }, [token]);

  // Re-fetches whenever the auth token changes: once on mount (no token — platform default or
  // whatever anonymous tenant resolution applies), then again after login/logout/tenant switch,
  // each time picking up the now-current tenant's branding instead of only ever fetching once
  // before any tenant was resolvable.
  useEffect(() => {
    let cancelled = false;
    fetchBranding(token)
      .then(data => { if (!cancelled) setBranding(data); })
      .catch(() => {}); // silent fallback to whatever branding is already applied
    return () => { cancelled = true; };
  }, [token]);

  // Applied whenever branding itself changes (not just on mount) — CSS custom properties, the
  // favicon/title, and any tenant custom CSS. Keeping the custom-CSS injection in this same
  // effect (rather than the fetch effect above) matters: reading branding.customCss from a
  // mount-only effect's closure would always see the still-default value, since the fetch above
  // resolves later, after that effect already ran once.
  useEffect(() => {
    const primaryRgb = hexToRgbTriplet(branding.primaryColor);
    const accentRgb = hexToRgbTriplet(branding.accentColor);
    if (primaryRgb) document.documentElement.style.setProperty('--color-primary', primaryRgb);
    if (accentRgb) document.documentElement.style.setProperty('--color-accent', accentRgb);

    if (branding.faviconUrl) {
      const link = document.querySelector<HTMLLinkElement>("link[rel~='icon']");
      if (link) link.href = branding.faviconUrl;
    }

    if (branding.productName) {
      document.title = branding.productName;
    }

    if (branding.customCss) {
      const style = document.createElement('style');
      style.id = 'tenant-custom-css';
      style.textContent = branding.customCss;
      document.head.appendChild(style);
      return () => { document.getElementById('tenant-custom-css')?.remove(); };
    }
  }, [branding]);

  return (
    <BrandingContext.Provider value={{ ...branding, refreshBranding: refresh }}>
      {children}
    </BrandingContext.Provider>
  );
}

export function useBranding() {
  return useContext(BrandingContext);
}
