import fetch from 'node-fetch';

const API_URL = process.env.DOCS_API_URL || 'http://localhost:8082';
const API_KEY  = process.env.DOCS_API_KEY || '';

export async function queryDocs(question, product = null, version = null, chatId = null) {
  const body = { question };
  if (product) body.product = product;
  if (version) body.version = version;
  if (chatId) body.chatId = chatId;

  const res = await fetch(`${API_URL}/api/v1/query`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-API-Key': API_KEY },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `API error ${res.status}`);
  }
  return res.json();
}
