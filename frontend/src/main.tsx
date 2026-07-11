import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { BrandingProvider } from './context/BrandingContext';
import { ToastProvider } from './components/ui/Toast';
import { CommandPaletteProvider } from './components/CommandPalette/CommandPaletteProvider';
import App from './App.tsx';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename="/docs-inator">
      <ThemeProvider>
        <BrandingProvider>
          <ToastProvider>
            <AuthProvider>
              <CommandPaletteProvider>
                <App />
              </CommandPaletteProvider>
            </AuthProvider>
          </ToastProvider>
        </BrandingProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>
);
