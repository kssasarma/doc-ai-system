// `??` (not `||`) matters here: the production Docker build sets these build-time vars to the
// empty string on purpose (see frontend/Dockerfile) so the bundle calls same-origin relative
// paths through the nginx reverse proxy instead of a baked-in absolute URL. `||` would treat
// that empty string as "unset" and silently fall back to the localhost dev default in prod.
export const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8082';
export const INGESTOR_URL = import.meta.env.VITE_INGESTOR_URL ?? 'http://localhost:8081';
