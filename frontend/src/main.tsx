import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { BrandingProvider } from './context/BrandingContext';
import { ToastProvider } from './components/ui/Toast';
import { CommandPaletteProvider } from './components/CommandPalette/CommandPaletteProvider';
import { installHttpInterceptors } from './lib/httpInterceptors';
import App from './App.tsx';
import './index.css';

installHttpInterceptors();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename="/docs-inator">
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>
            {/* Needs the auth token to re-fetch the logged-in tenant's branding once known —
                must be nested inside AuthProvider, not alongside/above it. */}
            <BrandingProvider>
              <CommandPaletteProvider>
                <App />
              </CommandPaletteProvider>
            </BrandingProvider>
          </AuthProvider>
        </ToastProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>
);
