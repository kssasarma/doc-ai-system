import React, { createContext, useContext, useEffect, useState } from 'react';
import { fetchBranding, type TenantBranding } from '../services/brandingService';

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

const BrandingContext = createContext<TenantBranding>(DEFAULT_BRANDING);

export function BrandingProvider({ children }: { children: React.ReactNode }) {
  const [branding, setBranding] = useState<TenantBranding>(DEFAULT_BRANDING);

  useEffect(() => {
    fetchBranding()
      .then(setBranding)
      .catch(() => {}); // silent fallback to defaults

    // Apply custom CSS if present
    if (branding.customCss) {
      const style = document.createElement('style');
      style.id = 'tenant-custom-css';
      style.textContent = branding.customCss;
      document.head.appendChild(style);
      return () => { document.getElementById('tenant-custom-css')?.remove(); };
    }
  }, []);

  // Apply CSS custom properties whenever branding changes — a tenant's chosen brand color
  // becomes the design system's `primary`/`accent` tokens everywhere (buttons, links, focus
  // rings), in both light and dark theme (brand identity doesn't change with theme).
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
  }, [branding]);

  return (
    <BrandingContext.Provider value={branding}>
      {children}
    </BrandingContext.Provider>
  );
}

export function useBranding() {
  return useContext(BrandingContext);
}
