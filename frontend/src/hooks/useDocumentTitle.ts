import { useEffect } from 'react';
import { useBranding } from '../context/BrandingContext';

/** Sets `document.title` to "`pageTitle` · `productName`" (or just the product name if omitted)
 * — Phase 6.8. Composes with the tenant's branding so a custom product name still wins. */
export function useDocumentTitle(pageTitle?: string) {
  const { productName } = useBranding();
  useEffect(() => {
    document.title = pageTitle ? `${pageTitle} · ${productName}` : productName;
  }, [pageTitle, productName]);
}
