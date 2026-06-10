import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { AuthUser } from '../types';
import { login as apiLogin, register as apiRegister, getMe } from '../services/authService';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'docai_token';

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
          setUser({
            userId: data.userId,
            username: data.username!,
            email: data.email!,
            role: data.role as 'ADMIN' | 'USER',
          });
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
    setUser({
      userId: data.userId!,
      username: data.username!,
      email: data.email!,
      role: data.role as 'ADMIN' | 'USER',
    });
  }, []);

  const register = useCallback(async (username: string, email: string, password: string) => {
    const data = await apiRegister(username, email, password);
    if (data.error || !data.token) {
      throw new Error(data.error || 'Registration failed');
    }
    localStorage.setItem(TOKEN_KEY, data.token);
    setToken(data.token);
    setUser({
      userId: data.userId!,
      username: data.username!,
      email: data.email!,
      role: data.role as 'ADMIN' | 'USER',
    });
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
      isAdmin: user?.role === 'ADMIN',
      login,
      register,
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
