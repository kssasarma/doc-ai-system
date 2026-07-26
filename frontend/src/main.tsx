import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MotionConfig } from 'framer-motion';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { BrandingProvider } from './context/BrandingContext';
import { ToastProvider } from './components/ui/Toast';
import { ConfirmProvider } from './components/ui/ConfirmDialog';
import { CommandPaletteProvider } from './components/CommandPalette/CommandPaletteProvider';
import { installHttpInterceptors } from './lib/httpInterceptors';
import { ROUTER_BASENAME } from './config/env';
import ErrorBoundary from './components/ErrorBoundary';
import App from './App.tsx';
import './index.css';

installHttpInterceptors();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* Respects the OS/browser prefers-reduced-motion setting for every framer-motion animation
          app-wide (Phase 6.8) — "user" defers to that setting rather than forcing one way. */}
      <MotionConfig reducedMotion="user">
        {/* Basename follows VITE_BASE_PATH (via Vite's BASE_URL) so routing and asset URLs can
            never disagree about where the app is mounted. */}
        <BrowserRouter basename={ROUTER_BASENAME}>
          <ThemeProvider>
            <ToastProvider>
              <ConfirmProvider>
                <AuthProvider>
                  {/* Needs the auth token to re-fetch the logged-in tenant's branding once known —
                      must be nested inside AuthProvider, not alongside/above it. */}
                  <BrandingProvider>
                    <CommandPaletteProvider>
                      <ErrorBoundary>
                        <App />
                      </ErrorBoundary>
                    </CommandPaletteProvider>
                  </BrandingProvider>
                </AuthProvider>
              </ConfirmProvider>
            </ToastProvider>
          </ThemeProvider>
        </BrowserRouter>
      </MotionConfig>
    </QueryClientProvider>
  </StrictMode>
);
