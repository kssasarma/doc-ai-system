// Central, typed access to every build-time configurable value. All of these come from .env
// (or the shell environment / Docker build args) via Vite's import.meta.env; the fallbacks
// keep a plain `npm run dev` with no .env file working out of the box.
import appConfig from './app.json';

const env = import.meta.env;

/** The app's base path with a trailing slash (e.g. "/docs-inator/"). Vite derives BASE_URL from
 * the `base` option, which vite.config.ts reads from VITE_BASE_PATH — so asset URLs, the router
 * basename, and nginx all follow the same env var. */
export const BASE_PATH = env.BASE_URL;

/** BASE_PATH shaped for react-router's `basename` prop: no trailing slash ("/docs-inator"),
 * or "/" when the app is served from the origin root. */
export const ROUTER_BASENAME = BASE_PATH.replace(/\/+$/, '') || '/';

// `??` (not `||`) matters here: the production Docker build sets these build-time vars to the
// empty string on purpose (see frontend/Dockerfile) so the bundle calls same-origin relative
// paths through the nginx reverse proxy instead of a baked-in absolute URL. `||` would treat
// that empty string as "unset" and silently fall back to the localhost dev default in prod.
export const BACKEND_URL = env.VITE_BACKEND_URL ?? 'http://localhost:8082';
export const INGESTOR_URL = env.VITE_INGESTOR_URL ?? 'http://localhost:8081';

// Branding/UI values fall back to src/config/app.json — `||` is correct for these, since an
// empty string just means "unset, use the default".
export const APP_TITLE = env.VITE_APP_TITLE || appConfig.app.title;
export const APP_SUBTITLE = env.VITE_APP_SUBTITLE || appConfig.app.subtitle;
export const APP_DESCRIPTION = env.VITE_APP_DESCRIPTION || appConfig.app.description;

export const MAX_MESSAGE_LENGTH =
  Number(env.VITE_MAX_MESSAGE_LENGTH) || appConfig.ui.chat.maxMessageLength;
