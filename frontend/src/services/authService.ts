import { AuthResponse } from '../types';

const BOT_URL = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8082'}`;
const AUTH_URL = `${BOT_URL}/api/auth`;

export async function bootstrap(username: string, email: string, password: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/bootstrap`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password }),
  });
  const data = await res.json();
  if (!res.ok) return { error: data.error || `Bootstrap failed: ${res.status}` };
  return data;
}

export async function login(username: string, password: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  return res.json();
}

export async function getMe(token: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Unauthorized');
  return res.json();
}
