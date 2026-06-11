import React, { createContext, useContext, useEffect, useState } from 'react';
import { fetchBranding, type TenantBranding } from '../services/brandingService';

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

  // Apply CSS custom properties whenever branding changes
  useEffect(() => {
    document.documentElement.style.setProperty('--color-primary', branding.primaryColor);
    document.documentElement.style.setProperty('--color-accent', branding.accentColor);

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
