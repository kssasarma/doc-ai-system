import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { AuthUser, AuthResponse } from '../types';
import { login as apiLogin, getMe } from '../services/authService';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isSuperAdmin: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  applySession: (data: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'docai_token';

// AuthResponse (login/me/bootstrap/accept-invite) never carries tenantId — only the
// JWT payload does. Decode it client-side rather than relying on the response body.
function decodeTenantIdFromToken(token: string): string | null {
  try {
    const payload = token.split('.')[1];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return json.tenantId ?? null;
  } catch {
    return null;
  }
}

function toAuthUser(data: AuthResponse, token: string): AuthUser {
  return {
    userId: data.userId!,
    username: data.username!,
    email: data.email!,
    role: data.role as AuthUser['role'],
    tenantId: decodeTenantIdFromToken(token),
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [isLoading, setIsLoading] = useState(true);

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
        } else {
          localStorage.removeItem(TOKEN_KEY);
          setToken(null);
        }
      })
      .catch(() => {
        localStorage.removeItem(TOKEN_KEY);
        setToken(null);
      })
      .finally(() => setIsLoading(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const data = await apiLogin(username, password);
    if (data.error || !data.token) {
      throw new Error(data.error || 'Login failed');
    }
    localStorage.setItem(TOKEN_KEY, data.token);
    setToken(data.token);
    setUser(toAuthUser(data, data.token));
  }, []);

  // Used by bootstrap / accept-invite flows, which return the same AuthResponse shape as login.
  const applySession = useCallback((data: AuthResponse) => {
    if (!data.token) return;
    localStorage.setItem(TOKEN_KEY, data.token);
    setToken(data.token);
    setUser(toAuthUser(data, data.token));
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
    setUser(null);
  }, []);

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
