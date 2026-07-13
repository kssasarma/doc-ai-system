import axios from 'axios';
import { notifyAuthExpired } from './authEvents';

/**
 * Central 401 handling. Previously every service just returned a generic error string on any
 * failure, including an expired JWT — nothing detected 401 specifically to log the user out and
 * route them back to the login screen, so an expired session just meant silently-failing pages
 * until someone manually cleared storage.
 *
 * Covers both HTTP clients used across the app's services: axios (a handful of admin/tenant
 * services) via its interceptor API, and the global fetch() that the rest of them call directly
 * (fetch has no built-in interceptor hook, so this wraps window.fetch once at startup).
 */
let installed = false;

export function installHttpInterceptors(): void {
  if (installed) return;
  installed = true;

  axios.interceptors.response.use(
    response => response,
    error => {
      if (error?.response?.status === 401) notifyAuthExpired();
      return Promise.reject(error);
    }
  );

  const originalFetch = window.fetch.bind(window);
  window.fetch = async (...args: Parameters<typeof fetch>) => {
    const response = await originalFetch(...args);
    if (response.status === 401) notifyAuthExpired();
    return response;
  };
}
