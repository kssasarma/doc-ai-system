// Kept as a re-export so the many existing `from '../config/backend'` imports stay valid;
// the values (and the note about `??` vs `||`) live in ./env.ts alongside the rest of the
// env-driven configuration.
export { BACKEND_URL, INGESTOR_URL } from './env';
