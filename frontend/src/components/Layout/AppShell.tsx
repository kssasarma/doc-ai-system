import React, { Suspense, lazy, useState } from 'react';
import AccountMenu from '../ui/AccountMenu';
import Footer from './Footer';

const PreferencesModal = lazy(() => import('../Settings/PreferencesModal'));

/** Persistent shell for every non-admin authenticated page — chat, bookmarks, collections, FAQ,
 * subscriptions, API keys. Owns the one top-right account menu (and the Preferences modal it
 * opens) so every page gets it for free instead of each page wiring its own. The admin console
 * has its own equivalent header (see AdminLayout) rather than nesting this shell around it too —
 * stacking two top bars would be redundant. */
export default function AppShell({ children }: { children: React.ReactNode }) {
  const [prefsOpen, setPrefsOpen] = useState(false);

  return (
    <div className="h-screen flex flex-col bg-background">
      <header className="h-12 flex-shrink-0 border-b border-border bg-surface flex items-center justify-end px-3">
        <AccountMenu onOpenPreferences={() => setPrefsOpen(true)} />
      </header>
      <div className="flex-1 min-h-0">
        {children}
      </div>
      <Footer />
      {prefsOpen && (
        <Suspense fallback={null}>
          <PreferencesModal onClose={() => setPrefsOpen(false)} />
        </Suspense>
      )}
    </div>
  );
}
