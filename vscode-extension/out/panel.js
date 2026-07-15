"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.DocsPanelProvider = void 0;
const vscode = __importStar(require("vscode"));
class DocsPanelProvider {
    constructor(_extensionUri) {
        this._extensionUri = _extensionUri;
    }
    resolveWebviewView(webviewView) {
        this._view = webviewView;
        webviewView.webview.options = {
            enableScripts: true,
            localResourceRoots: [this._extensionUri],
        };
        webviewView.webview.html = this.getHtml();
        webviewView.webview.onDidReceiveMessage(async (msg) => {
            if (msg.type === 'query') {
                await this.handleQuery(msg.question, msg.chatId);
            }
            else if (msg.type === 'getConfig') {
                this.sendConfig();
            }
        });
        this.sendConfig();
    }
    /** Ask a question pre-filled (e.g. from editor selection) */
    askQuestion(question) {
        if (this._view) {
            this._view.show(true);
            this._view.webview.postMessage({ type: 'prefill', question });
        }
    }
    sendConfig() {
        const cfg = vscode.workspace.getConfiguration('docsinator');
        this._view?.webview.postMessage({
            type: 'config',
            apiUrl: cfg.get('apiUrl', ''),
            apiKey: cfg.get('apiKey', ''),
            defaultProduct: cfg.get('defaultProduct', ''),
            defaultVersion: cfg.get('defaultVersion', ''),
        });
    }
    async handleQuery(question, chatId) {
        const cfg = vscode.workspace.getConfiguration('docsinator');
        const apiUrl = cfg.get('apiUrl', '').replace(/\/$/, '');
        const apiKey = cfg.get('apiKey', '');
        const product = cfg.get('defaultProduct', '') || undefined;
        const version = cfg.get('defaultVersion', '') || undefined;
        if (!apiUrl || !apiKey) {
            this._view?.webview.postMessage({
                type: 'error',
                message: 'API URL or API Key not configured. Run "Docs-inator: Configure API connection".',
            });
            return;
        }
        try {
            const body = { question };
            if (product)
                body.product = product;
            if (version)
                body.version = version;
            if (chatId)
                body.chatId = chatId;
            const res = await fetch(`${apiUrl}/api/v1/query`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-API-Key': apiKey },
                body: JSON.stringify(body),
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.message ?? `API error ${res.status}`);
            }
            const data = await res.json();
            this._view?.webview.postMessage({ type: 'answer', data });
        }
        catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            this._view?.webview.postMessage({ type: 'error', message });
        }
    }
    getHtml() {
        const nonce = getNonce();
        return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: var(--vscode-font-family); font-size: 13px; color: var(--vscode-foreground); background: var(--vscode-sideBar-background); display: flex; flex-direction: column; height: 100vh; }
#messages { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 6px; }
.msg { padding: 8px 10px; border-radius: 6px; line-height: 1.5; max-width: 95%; word-break: break-word; }
.msg.user { background: var(--vscode-button-background); color: var(--vscode-button-foreground); align-self: flex-end; border-bottom-right-radius: 2px; }
.msg.bot { background: var(--vscode-editor-inactiveSelectionBackground); align-self: flex-start; border-bottom-left-radius: 2px; }
.msg.error { background: var(--vscode-inputValidation-errorBackground); color: var(--vscode-inputValidation-errorForeground); }
.msg .conf { font-size: 11px; margin-top: 4px; opacity: 0.75; }
.msg .sources { font-size: 11px; margin-top: 4px; opacity: 0.7; border-top: 1px solid var(--vscode-editorWidget-border); padding-top: 4px; }
.thinking { font-style: italic; opacity: 0.6; }
.input-row { display: flex; gap: 4px; padding: 8px; border-top: 1px solid var(--vscode-editorWidget-border); }
.input-row input { flex: 1; background: var(--vscode-input-background); color: var(--vscode-input-foreground); border: 1px solid var(--vscode-input-border); border-radius: 4px; padding: 5px 8px; font-size: 13px; outline: none; }
.input-row input:focus { border-color: var(--vscode-focusBorder); }
.input-row button { background: var(--vscode-button-background); color: var(--vscode-button-foreground); border: none; border-radius: 4px; padding: 5px 10px; cursor: pointer; }
.input-row button:disabled { opacity: 0.5; cursor: default; }
.open-link { font-size: 11px; padding: 0 8px 6px; color: var(--vscode-textLink-foreground); text-decoration: none; display: block; }
.open-link:hover { text-decoration: underline; }
.hidden { display: none !important; }
</style>
</head>
<body>
<div id="messages"></div>
<a id="open-link" class="open-link hidden" href="#" target="_blank">Open in web app ↗</a>
<div class="input-row">
  <input id="input" type="text" placeholder="Ask about your docs…" autocomplete="off" />
  <button id="send">➤</button>
</div>
<script nonce="${nonce}">
const vscode = acquireVsCodeApi();
let chatId = null;
let appUrl = '';
const msgs = document.getElementById('messages');
const input = document.getElementById('input');
const send = document.getElementById('send');
const openLink = document.getElementById('open-link');

function addMsg(role, html) {
  const d = document.createElement('div');
  d.className = 'msg ' + role;
  d.innerHTML = html;
  msgs.appendChild(d);
  msgs.scrollTop = msgs.scrollHeight;
  return d;
}

function esc(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function confLabel(l) {
  return l === 'HIGH' ? '🟢 High confidence' : l === 'MEDIUM' ? '🟡 Medium confidence' : '🔴 Low confidence';
}

function doSend() {
  const q = input.value.trim();
  if (!q) return;
  input.value = '';
  addMsg('user', esc(q));
  const thinking = addMsg('bot', '<span class="thinking">Searching…</span>');
  send.disabled = true;
  vscode.postMessage({ type: 'query', question: q, chatId });
  window._thinking = thinking;
}

send.addEventListener('click', doSend);
input.addEventListener('keydown', e => { if (e.key === 'Enter') doSend(); });

window.addEventListener('message', e => {
  const msg = e.data;
  if (msg.type === 'config') {
    appUrl = msg.apiUrl || '';
  } else if (msg.type === 'prefill') {
    input.value = msg.question;
    doSend();
  } else if (msg.type === 'answer') {
    const d = msg.data;
    chatId = d.chatId || chatId;
    const sourcesHtml = d.sources?.length
      ? '<div class="sources">Sources: ' + d.sources.slice(0,3).map(s => '<em>' + esc(s.document) + (s.version ? ' v' + esc(s.version) : '') + '</em>').join(', ') + '</div>'
      : '';
    if (window._thinking) {
      window._thinking.innerHTML = esc(d.answer) + '<div class="conf">' + confLabel(d.confidenceLabel) + '</div>' + sourcesHtml;
      window._thinking = null;
    }
    if (appUrl && chatId) {
      openLink.href = appUrl + '/chat/' + chatId;
      openLink.classList.remove('hidden');
    }
    send.disabled = false;
    input.focus();
  } else if (msg.type === 'error') {
    if (window._thinking) {
      window._thinking.className = 'msg error';
      window._thinking.textContent = msg.message;
      window._thinking = null;
    }
    send.disabled = false;
  }
});

vscode.postMessage({ type: 'getConfig' });
</script>
</body>
</html>`;
    }
}
exports.DocsPanelProvider = DocsPanelProvider;
DocsPanelProvider.viewType = 'docsinator.chatView';
function getNonce() {
    let text = '';
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    for (let i = 0; i < 32; i++) {
        text += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return text;
}
//# sourceMappingURL=panel.js.map