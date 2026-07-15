import { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from 'react';
import { AuthUser, AuthResponse } from '../types';
import { login as apiLogin, getMe, changePassword as apiChangePassword, refreshSession, revokeSession } from '../services/authService';
import { registerAuthExpiredHandler } from '../lib/authEvents';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isSuperAdmin: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  applySession: (data: AuthResponse) => void;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'docai_token';
const REFRESH_TOKEN_KEY = 'docai_refresh_token';

// Renew this long before the access token's real expiry — leaves headroom for clock skew and for
// the renewal request itself to complete before the old token would actually stop working.
const RENEW_BEFORE_EXPIRY_MS = 60_000;
const MIN_RENEW_DELAY_MS = 5_000;

// AuthResponse (login/me/accept-invite) never carries tenantId or mustChangePassword as of the
// original design — only the JWT payload does. Decode it client-side rather than relying on the
// response body, which stays true after /change-password reissues a fresh token too.
function decodeClaimsFromToken(token: string): { tenantId: string | null; mustChangePassword: boolean; exp: number | null } {
  try {
    const payload = token.split('.')[1];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return { tenantId: json.tenantId ?? null, mustChangePassword: json.mustChangePassword ?? false, exp: json.exp ?? null };
  } catch {
    return { tenantId: null, mustChangePassword: false, exp: null };
  }
}

function toAuthUser(data: AuthResponse, token: string): AuthUser {
  const claims = decodeClaimsFromToken(token);
  return {
    userId: data.userId!,
    username: data.username!,
    email: data.email!,
    role: data.role as AuthUser['role'],
    tenantId: claims.tenantId,
    mustChangePassword: claims.mustChangePassword,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [isLoading, setIsLoading] = useState(true);
  const renewTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Refresh tokens are single-use (server-side rotation + reuse detection) — if the proactive
  // timer and a reactive 401 both fire around the same moment, or several requests 401 in
  // parallel, naively calling performRefresh() from each would race two rotations of the same
  // token and the loser would look like a replay attack, revoking every session for the user.
  // Sharing one in-flight promise makes concurrent callers await the same rotation instead.
  const inFlightRefreshRef = useRef<Promise<boolean> | null>(null);

  // Plain function declarations (not useCallback) — they're mutually recursive (schedule → renew
  // → apply session → reschedule) and interact with the world only via refs/localStorage/setState
  // setters, never stale render-scoped state, so redefining them each render is harmless and sidesteps
  // an awkward forward-reference dance between memoized callbacks.

  function clearSession() {
    if (renewTimerRef.current) { clearTimeout(renewTimerRef.current); renewTimerRef.current = null; }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    setToken(null);
    setUser(null);
  }

  function scheduleRenewal(accessToken: string) {
    if (renewTimerRef.current) clearTimeout(renewTimerRef.current);
    const { exp } = decodeClaimsFromToken(accessToken);
    if (!exp) return;
    const delay = Math.max(exp * 1000 - Date.now() - RENEW_BEFORE_EXPIRY_MS, MIN_RENEW_DELAY_MS);
    renewTimerRef.current = setTimeout(() => { performRefresh(); }, delay);
  }

  function applySessionInternal(data: AuthResponse) {
    if (!data.token) return;
    localStorage.setItem(TOKEN_KEY, data.token);
    if (data.refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
    setToken(data.token);
    setUser(toAuthUser(data, data.token));
    scheduleRenewal(data.token);
  }

  function performRefresh(): Promise<boolean> {
    if (inFlightRefreshRef.current) return inFlightRefreshRef.current;
    const attempt = (async () => {
      const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
      if (!storedRefreshToken) return false;
      try {
        const data = await refreshSession(storedRefreshToken);
        if (data.error || !data.token) return false;
        applySessionInternal(data);
        return true;
      } catch {
        return false;
      } finally {
        inFlightRefreshRef.current = null;
      }
    })();
    inFlightRefreshRef.current = attempt;
    return attempt;
  }

  // Restore session from stored token
  useEffect(() => {
    const storedToken = localStorage.getItem(TOKEN_KEY);
    if (!storedToken) {
      setIsLoading(false);
      return;
    }
    getMe(storedToken)
      .then(data => {
        if (data.userId && data.role) {
          setUser(toAuthUser(data, storedToken));
          setToken(storedToken);
          scheduleRenewal(storedToken);
        } else {
          clearSession();
        }
      })
      .catch(async () => {
        // The stored access token is dead (expired/invalid) — try a silent renewal before giving
        // up, so a returning visitor with a still-valid refresh token doesn't get bounced to login.
        const renewed = await performRefresh();
        if (!renewed) clearSession();
      })
      .finally(() => setIsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const data = await apiLogin(username, password);
    if (data.error || !data.token) {
      throw new Error(data.error || 'Login failed');
    }
    applySessionInternal(data);
  }, []);

  // Used by accept-invite / change-password flows, which return the same AuthResponse shape as login.
  const applySession = useCallback((data: AuthResponse) => {
    applySessionInternal(data);
  }, []);

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    if (!token) throw new Error('Not authenticated');
    const data = await apiChangePassword(token, currentPassword, newPassword);
    if (data.error || !data.token) {
      throw new Error(data.error || 'Password change failed');
    }
    applySessionInternal(data);
  }, [token]);

  const logout = useCallback(() => {
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (storedRefreshToken) revokeSession(storedRefreshToken);
    clearSession();
  }, []);

  // Lets any service's HTTP call — not just this context's own — respond to a 401 (expired/
  // invalid JWT) by trying one silent renewal first; only logs out if that renewal itself fails
  // (the refresh token is also dead). See lib/httpInterceptors.ts.
  useEffect(() => {
    registerAuthExpiredHandler(async () => {
      const renewed = await performRefresh();
      if (!renewed) logout();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [logout]);

  return (
    <AuthContext.Provider value={{
      user,
      token,
      isLoading,
      isAuthenticated: !!user,
      isSuperAdmin: user?.role === 'SUPER_ADMIN',
      isAdmin: user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN',
      login,
      applySession,
      changePassword,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
