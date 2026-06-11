const $ = (id) => document.getElementById(id);

let config = {};
let currentChatId = null;
let appUrl = null;

async function loadConfig() {
  return new Promise((resolve) => {
    chrome.storage.sync.get(['apiUrl', 'apiKey', 'defaultProduct', 'defaultVersion'], (items) => {
      config = items;
      resolve(items);
    });
  });
}

function saveConfig(cfg) {
  return new Promise((resolve) => {
    chrome.storage.sync.set(cfg, resolve);
  });
}

function showView(name) {
  document.querySelectorAll('.view').forEach(v => v.classList.add('hidden'));
  $(name + '-view').classList.remove('hidden');
}

function addMessage(role, html) {
  const msgs = $('messages');
  const div = document.createElement('div');
  div.className = `message ${role}`;
  div.innerHTML = html;
  msgs.appendChild(div);
  msgs.scrollTop = msgs.scrollHeight;
  return div;
}

function confLabel(label) {
  const map = { HIGH: '🟢 High', MEDIUM: '🟡 Medium', LOW: '🔴 Low' };
  return map[label] ?? label;
}

async function sendQuestion(question) {
  if (!question.trim()) return;

  addMessage('user', escHtml(question));
  const thinking = addMessage('bot', '<span class="thinking">Searching…</span>');

  $('send-btn').disabled = true;
  $('question-input').value = '';

  try {
    const body = { question };
    if (config.defaultProduct) body.product = config.defaultProduct;
    if (config.defaultVersion) body.version = config.defaultVersion;
    if (currentChatId) body.chatId = currentChatId;

    const res = await fetch(`${config.apiUrl}/api/v1/query`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-API-Key': config.apiKey },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `Error ${res.status}`);
    }

    const data = await res.json();
    currentChatId = data.chatId ?? currentChatId;

    const sourcesHtml = data.sources?.length
      ? `<div class="sources">Sources: ${data.sources.slice(0, 3).map(s =>
          `<em>${escHtml(s.document)}${s.version ? ` v${escHtml(s.version)}` : ''}</em>`
        ).join(', ')}</div>`
      : '';

    thinking.innerHTML =
      escHtml(data.answer) +
      `<div class="confidence">${confLabel(data.confidenceLabel)}</div>` +
      sourcesHtml;

    if (appUrl && currentChatId) {
      const link = $('open-app-link');
      link.href = `${appUrl}/chat/${currentChatId}`;
      link.classList.remove('hidden');
    }
  } catch (err) {
    thinking.className = 'message error';
    thinking.textContent = `Error: ${err.message}`;
  } finally {
    $('send-btn').disabled = false;
    $('question-input').focus();
  }
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function init() {
  const cfg = await loadConfig();

  if (!cfg.apiUrl || !cfg.apiKey) {
    showView('setup');
    $('api-url').focus();
  } else {
    appUrl = cfg.apiUrl;
    showView('main');
    $('question-input').focus();
  }

  // Check for selected text passed from content script
  chrome.storage.local.get(['pendingQuestion'], ({ pendingQuestion }) => {
    if (pendingQuestion) {
      chrome.storage.local.remove('pendingQuestion');
      if ($('question-input')) {
        $('question-input').value = pendingQuestion;
        sendQuestion(pendingQuestion);
      }
    }
  });

  // Setup form
  $('setup-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const newCfg = {
      apiUrl: $('api-url').value.replace(/\/$/, ''),
      apiKey: $('api-key').value.trim(),
      defaultProduct: $('default-product').value.trim() || undefined,
      defaultVersion: $('default-version').value.trim() || undefined,
    };
    await saveConfig(newCfg);
    Object.assign(config, newCfg);
    appUrl = newCfg.apiUrl;
    showView('main');
    $('question-input').focus();
  });

  // Settings button
  $('settings-btn')?.addEventListener('click', () => {
    $('api-url').value = config.apiUrl ?? '';
    $('api-key').value = config.apiKey ?? '';
    $('default-product').value = config.defaultProduct ?? '';
    $('default-version').value = config.defaultVersion ?? '';
    showView('setup');
  });

  // Send
  $('send-btn').addEventListener('click', () => sendQuestion($('question-input').value));
  $('question-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendQuestion($('question-input').value); }
  });
}

init();
