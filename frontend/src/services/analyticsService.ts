import { BACKEND_URL } from '../config/backend';
import { ApiResult } from '../types';

const BOT_URL = BACKEND_URL;

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function get<T>(path: string, token: string): Promise<ApiResult<T>> {
  try {
    const res = await fetch(`${BOT_URL}${path}`, { headers: authHeaders(token) });
    const data = await res.json();
    if (!res.ok) return { success: false, error: data.message || 'Request failed' };
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Network error' };
  }
}

export interface OverviewStats {
  totalQueriesAllTime: number;
  queriesToday: number;
  queriesThisWeek: number;
  queriesThisMonth: number;
  dauToday: number;
  wauThisWeek: number;
  mauThisMonth: number;
  avgSessionLength: number;
  totalPositiveFeedback: number;
  totalNegativeFeedback: number;
  avgConfidence: number;
}

export interface DailyStat {
  date: string;
  queryCount: number;
  avgConfidence: number;
  estimatedCost: number;
}

export interface TopQuestion {
  questionPreview: string;
  count: number;
  product: string | null;
  version: string | null;
}

export interface ProductCoverage {
  product: string;
  version: string | null;
  queryCount: number;
  avgConfidence: number;
  lowConfidenceCount: number;
  lowConfidencePct: number;
}

export interface UserEngagement {
  userId: string;
  username: string;
  queryCount: number;
  avgConfidence: number;
  lastActive: string | null;
}

export interface CostSummary {
  totalCostThisMonth: number;
  totalCostAllTime: number;
  avgCostPerQuery: number;
  dailyCost: DailyStat[];
  costByUser: UserCost[];
  costByProduct: ProductCost[];
}

export interface UserCost {
  userId: string;
  username: string;
  totalCost: number;
  queryCount: number;
}

export interface ProductCost {
  product: string;
  version: string | null;
  totalCost: number;
  queryCount: number;
}

export interface DocumentCoverage {
  documentName: string;
  product: string | null;
  version: string | null;
  citationCount: number;
}

export interface FailedQuery {
  questionPreview: string;
  count: number;
  product: string | null;
  version: string | null;
}

export const fetchOverview = (token: string) =>
  get<OverviewStats>('/api/admin/analytics/overview', token);

export const fetchDailyStats = (token: string, days = 30) =>
  get<DailyStat[]>(`/api/admin/analytics/daily?days=${days}`, token);

export const fetchTopQuestions = (token: string, limit = 10) =>
  get<TopQuestion[]>(`/api/admin/analytics/top-questions?limit=${limit}`, token);

export const fetchProductCoverage = (token: string) =>
  get<ProductCoverage[]>('/api/admin/analytics/product-coverage', token);

export const fetchUserEngagement = (token: string) =>
  get<UserEngagement[]>('/api/admin/analytics/user-engagement', token);

export const fetchCostSummary = (token: string) =>
  get<CostSummary>('/api/admin/analytics/cost', token);

export const fetchDocumentCoverage = (token: string) =>
  get<DocumentCoverage[]>('/api/admin/analytics/document-coverage', token);

export const fetchFailedQueries = (token: string, limit = 20) =>
  get<FailedQuery[]>(`/api/admin/analytics/failed-queries?limit=${limit}`, token);
