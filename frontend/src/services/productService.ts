import { ProductEntry } from '../types';

const BASE = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8082'}/api/products`;

/** Distinct product+version pairs the caller can actually search — backs the chat scope chip. */
export async function fetchAccessibleProducts(token: string): Promise<ProductEntry[]> {
  const res = await fetch(BASE, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
