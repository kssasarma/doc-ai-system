/**
 * Small pub/sub bridge between plain (non-React) HTTP code — services, interceptors — and
 * AuthContext. A 401 can happen from any of the ~20 service modules in this app, most of which
 * call fetch()/axios directly rather than going through a shared client; this lets all of them
 * react the same way (log out, fall back to the login screen) without each one importing
 * AuthContext directly, which they can't — they're plain modules, not components.
 */
type Listener = () => void;

let listener: Listener | null = null;

/** Called once by AuthProvider on mount, wiring its own logout() in as the handler. */
export function registerAuthExpiredHandler(fn: Listener): void {
  listener = fn;
}

/** Called by the HTTP interceptors below whenever a response comes back 401. */
export function notifyAuthExpired(): void {
  listener?.();
}
