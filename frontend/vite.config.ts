import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import appConfig from './src/config/app.json';

/** Replaces %VITE_*% placeholders in index.html with env values (or their defaults). Runs with
 * order 'pre' so it beats Vite's built-in HTML env replacement, which would leave a literal
 * `%VITE_APP_TITLE%` in the page (plus a warning) whenever the variable isn't set in .env —
 * this way the app.json defaults apply instead. */
function htmlEnv(values: Record<string, string>): Plugin {
  return {
    name: 'html-env',
    transformIndexHtml: {
      order: 'pre',
      handler: (html) =>
        Object.entries(values).reduce(
          (out, [key, value]) => out.replaceAll(`%${key}%`, value),
          html
        ),
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // '.' = the directory vite runs from (this package root); avoids process.cwd(), which would
  // drag in @types/node just for this line.
  const env = loadEnv(mode, '.', '');

  // Everything base-path-related (asset URLs, router basename via import.meta.env.BASE_URL,
  // the manifest link) follows this one variable. Vite requires a trailing slash on `base`,
  // so accept "/docs-inator" and "/docs-inator/" alike.
  const rawBase = env.VITE_BASE_PATH || '/docs-inator/';
  const base = rawBase.endsWith('/') ? rawBase : `${rawBase}/`;

  return {
    base,
    plugins: [
      react(),
      htmlEnv({
        VITE_APP_TITLE: env.VITE_APP_TITLE || appConfig.app.title,
        VITE_APP_SUBTITLE: env.VITE_APP_SUBTITLE || appConfig.app.subtitle,
        VITE_APP_DESCRIPTION: env.VITE_APP_DESCRIPTION || appConfig.app.description,
        VITE_THEME_COLOR: env.VITE_THEME_COLOR || '#2563eb',
      }),
    ],
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
    },
    build: {
      rollupOptions: {
        output: {
          // Split large, rarely-changing vendor libraries out of the main entry chunk — they cache
          // independently of app code (which changes every deploy) and independently of each
          // other, so a deploy that only touches app code doesn't invalidate the vendor chunks a
          // returning visitor already has cached.
          manualChunks: {
            'vendor-react': ['react', 'react-dom', 'react-router-dom'],
            'vendor-motion': ['framer-motion'],
            'vendor-http': ['axios'],
          },
        },
      },
    },
  };
});
