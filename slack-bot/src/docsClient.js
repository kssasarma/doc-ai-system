import fetch from 'node-fetch';

const API_URL  = process.env.DOCS_API_URL || 'http://localhost:8082';
const API_KEY  = process.env.DOCS_API_KEY || '';

// Fail fast at startup, not on the first confusing 401 a user hits mid-conversation — an unset
// key would otherwise silently send `X-API-Key: ` on every request.
if (!API_KEY) {
  throw new Error(
    'DOCS_API_KEY is not set. Create an API key under Settings → API Keys in the Docs-inator ' +
    'web app and set it as this bot\'s DOCS_API_KEY environment variable before starting.'
  );
}

/**
 * Query the Docs-inator /api/v1/query endpoint.
 * @param {string} question
 * @param {string|null} product
 * @param {string|null} version
 * @param {string|null} chatId - pass existing chatId to continue a conversation
 * @returns {Promise<{answer, confidence, confidenceLabel, sources, chatId, relatedQuestions}>}
 */
export async function queryDocs(question, product = null, version = null, chatId = null) {
  const body = { question };
  if (product) body.product = product;
  if (version) body.version = version;
  if (chatId) body.chatId = chatId;

  const res = await fetch(`${API_URL}/api/v1/query`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `API error ${res.status}`);
  }

  return res.json();
}

/**
 * Search docs (semantic, not conversational).
 */
export async function searchDocs(q, product = null, version = null) {
  const params = new URLSearchParams({ q });
  if (product) params.set('product', product);
  if (version) params.set('version', version);

  const res = await fetch(`${API_URL}/api/v1/search?${params}`, {
    headers: { 'X-API-Key': API_KEY },
  });

  if (!res.ok) throw new Error(`Search error ${res.status}`);
  return res.json();
}
