/// <reference types="vite/client" />

// Build-time configuration, sourced from .env / shell env / Docker build args.
// See frontend/.env.example for what each variable does; runtime access goes
// through src/config/env.ts, which owns the defaults.
interface ImportMetaEnv {
  /** Base path the app is served under (default "/docs-inator/"); feeds Vite's `base`. */
  readonly VITE_BASE_PATH?: string;
  /** Documentation-bot API origin; empty string = same-origin relative paths (prod). */
  readonly VITE_BACKEND_URL?: string;
  /** Document-ingestor API origin; empty string = same-origin relative paths (prod). */
  readonly VITE_INGESTOR_URL?: string;
  /** Product name shown in the UI and the HTML <title>/meta tags. */
  readonly VITE_APP_TITLE?: string;
  /** Tagline appended to the HTML <title>/meta tags. */
  readonly VITE_APP_SUBTITLE?: string;
  /** Longer description used in HTML meta/og tags. */
  readonly VITE_APP_DESCRIPTION?: string;
  /** <meta name="theme-color"> value for the HTML shell. */
  readonly VITE_THEME_COLOR?: string;
  /** Max characters accepted by the chat message input. */
  readonly VITE_MAX_MESSAGE_LENGTH?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
