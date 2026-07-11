import React, { createContext, lazy, Suspense, useCallback, useContext, useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';

const CommandPalette = lazy(() => import('./CommandPalette'));

const CommandPaletteContext = createContext<{ open: () => void } | null>(null);

/** Global Cmd/Ctrl+K palette — mounted once, listens app-wide. Only active for authenticated
 * users (the palette's actions all assume a signed-in session). */
export function CommandPaletteProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);
  const [everOpened, setEverOpened] = useState(false);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setEverOpened(true);
        setOpen(v => !v);
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);

  const openPalette = useCallback(() => { setEverOpened(true); setOpen(true); }, []);

  return (
    <CommandPaletteContext.Provider value={{ open: openPalette }}>
      {children}
      {isAuthenticated && everOpened && (
        <Suspense fallback={null}>
          <CommandPalette open={open} onClose={() => setOpen(false)} />
        </Suspense>
      )}
    </CommandPaletteContext.Provider>
  );
}

export function useCommandPalette() {
  const ctx = useContext(CommandPaletteContext);
  if (!ctx) throw new Error('useCommandPalette must be used inside CommandPaletteProvider');
  return ctx;
}
