import { useState } from 'react';
import { Save, KeyRound, Zap, CheckCircle2, XCircle, AlertTriangle } from 'lucide-react';
import {
  updateTenantLLMConfig, testTenantLLMConnection,
  type TenantLLMConfig, type TenantLLMConfigUpdate,
} from '../../services/tenantService';
import Button from '../ui/Button';
import Input from '../ui/Input';
import Select from '../ui/Select';
import Badge from '../ui/Badge';
import { useToast } from '../ui/Toast';

/**
 * Shared AI configuration form used by both the tenant-admin Settings page and the super-admin
 * Tenants page. Every AI-related setting is per-tenant — providers, models, endpoints, API keys
 * and generation options — and there is no platform fallback key: AI features stay disabled for
 * the tenant until a working chat key is saved here.
 *
 * Keys are write-only: the backend never returns them, only whether one is stored plus a
 * last-4-characters hint. Leaving a key field blank on save keeps whatever is already stored;
 * "Remove" explicitly clears it (disabling AI features until a new key is saved).
 */
export default function LlmConfigForm({ token, tenantId, config, onSaved, size = 'md' }: {
  token: string;
  tenantId: string;
  config: TenantLLMConfig;
  onSaved: (updated: TenantLLMConfig) => void;
  size?: 'sm' | 'md';
}) {
  const toast = useToast();
  const [form, setForm] = useState(config);
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [embeddingKeyInput, setEmbeddingKeyInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  const buttonSize = size === 'sm' ? 'sm' : 'md';
  const gap = size === 'sm' ? 'gap-2' : 'gap-3';

  // A dedicated embedding key is only needed when the embedding provider differs from the chat
  // provider (the chat key is reused when they match).
  const sameProvider = form.embeddingProvider === form.chatProvider;
  const embeddingKeyMissing = !form.hasEmbeddingKey && !(sameProvider && form.hasChatKey);

  const buildUpdate = (): TenantLLMConfigUpdate => ({
    chatProvider: form.chatProvider,
    chatModel: form.chatModel,
    chatBaseUrl: form.chatBaseUrl ?? null,
    embeddingProvider: form.embeddingProvider,
    embeddingModel: form.embeddingModel,
    embeddingBaseUrl: form.embeddingBaseUrl ?? null,
    routingEnabled: form.routingEnabled,
    simpleModel: form.simpleModel,
    complexModel: form.complexModel,
    azureEndpoint: form.azureEndpoint,
    azureDeployment: form.azureDeployment,
    // Untouched input = leave the stored key as-is; the field is never pre-filled with it.
    apiKey: apiKeyInput === '' ? undefined : apiKeyInput,
    embeddingApiKey: embeddingKeyInput === '' ? undefined : embeddingKeyInput,
    temperature: form.temperature,
    maxTokens: form.maxTokens,
    maxEmbeddingBatchTokens: form.maxEmbeddingBatchTokens ?? null,
  });

  const save = async () => {
    setSaving(true);
    setTestResult(null);
    try {
      const updated = await updateTenantLLMConfig(token, tenantId, buildUpdate());
      setForm(updated);
      setApiKeyInput('');
      setEmbeddingKeyInput('');
      onSaved(updated);
      toast.success('AI configuration saved.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to save AI configuration.');
    } finally {
      setSaving(false);
    }
  };

  const clearKey = async (which: 'chat' | 'embedding') => {
    setSaving(true);
    try {
      const update = which === 'chat'
        ? { ...buildUpdate(), apiKey: '' }
        : { ...buildUpdate(), embeddingApiKey: '' };
      const updated = await updateTenantLLMConfig(token, tenantId, update);
      setForm(updated);
      setApiKeyInput('');
      setEmbeddingKeyInput('');
      onSaved(updated);
      toast.success(which === 'chat'
        ? 'Chat API key removed — AI features are disabled for this tenant until a new key is saved.'
        : 'Embedding API key removed.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to remove API key.');
    } finally {
      setSaving(false);
    }
  };

  const testConnection = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testTenantLLMConnection(token, tenantId, {
        provider: form.chatProvider,
        model: form.chatModel,
        apiKey: apiKeyInput || undefined,
        baseUrl: form.chatBaseUrl || undefined,
      });
      setTestResult(result);
    } catch (e) {
      setTestResult({ success: false, message: e instanceof Error ? e.message : 'Test failed.' });
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className={`space-y-3`}>
      {!form.hasChatKey && (
        <div className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-2.5 text-xs text-foreground">
          <AlertTriangle size={14} className="mt-0.5 flex-shrink-0 text-warning" />
          <span>
            No API key is configured. AI features (chat, search, ingestion) are disabled for this
            tenant until an admin saves a provider API key below — there is no platform default key.
          </span>
        </div>
      )}

      <div className={`grid grid-cols-1 sm:grid-cols-2 ${gap}`}>
        <Select
          label="Chat Provider"
          value={form.chatProvider}
          onChange={e => setForm(c => ({ ...c, chatProvider: e.target.value }))}
        >
          <option value="openai">OpenAI</option>
          <option value="anthropic">Anthropic</option>
        </Select>
        <Input
          label="Chat Model"
          value={form.chatModel}
          onChange={e => setForm(c => ({ ...c, chatModel: e.target.value }))}
        />
        {size !== 'sm' && (
          <>
            <Input
              label="Chat base URL"
              placeholder="Provider default (e.g. https://api.openai.com)"
              value={form.chatBaseUrl ?? ''}
              onChange={e => setForm(c => ({ ...c, chatBaseUrl: e.target.value || null }))}
            />
            <div className={`grid grid-cols-2 ${gap}`}>
              <Input
                label="Temperature"
                type="number"
                step="0.1"
                min="0"
                max="2"
                value={form.temperature}
                onChange={e => setForm(c => ({ ...c, temperature: Number(e.target.value) }))}
              />
              <Input
                label="Max tokens"
                type="number"
                min="1"
                value={form.maxTokens}
                onChange={e => setForm(c => ({ ...c, maxTokens: Number(e.target.value) }))}
              />
            </div>
            <Select
              label="Embedding Provider"
              value={form.embeddingProvider}
              onChange={e => setForm(c => ({ ...c, embeddingProvider: e.target.value }))}
            >
              <option value="openai">OpenAI (or compatible endpoint)</option>
            </Select>
            <Input
              label="Embedding Model"
              value={form.embeddingModel}
              onChange={e => setForm(c => ({ ...c, embeddingModel: e.target.value }))}
            />
            <Input
              label="Embedding base URL"
              placeholder="Provider default (e.g. https://api.openai.com)"
              value={form.embeddingBaseUrl ?? ''}
              onChange={e => setForm(c => ({ ...c, embeddingBaseUrl: e.target.value || null }))}
            />
            <Input
              label="Max embedding batch tokens"
              placeholder="Default (match your embedding model's context window)"
              type="number"
              value={form.maxEmbeddingBatchTokens ?? ''}
              onChange={e => setForm(c => ({
                ...c,
                maxEmbeddingBatchTokens: e.target.value === '' ? null : Number(e.target.value),
              }))}
            />
          </>
        )}
      </div>

      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id={`routing-${tenantId}`}
          checked={form.routingEnabled}
          onChange={e => setForm(c => ({ ...c, routingEnabled: e.target.checked }))}
          className="h-4 w-4 rounded border-border accent-primary"
        />
        <label htmlFor={`routing-${tenantId}`} className="text-sm text-foreground">
          Enable smart routing (simple → cheap model, complex → powerful model)
        </label>
      </div>
      {form.routingEnabled && (
        <div className={`grid grid-cols-1 sm:grid-cols-2 ${gap}`}>
          <Input
            label="Simple queries model"
            value={form.simpleModel}
            onChange={e => setForm(c => ({ ...c, simpleModel: e.target.value }))}
          />
          <Input
            label="Complex queries model"
            value={form.complexModel}
            onChange={e => setForm(c => ({ ...c, complexModel: e.target.value }))}
          />
        </div>
      )}

      <div className="rounded-lg border border-border p-3 space-y-2">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div className="flex items-center gap-2 text-sm font-medium text-foreground">
            <KeyRound size={14} className="text-primary" /> Chat API Key
          </div>
          {form.hasChatKey ? (
            <Badge variant="success">Key configured {form.chatKeyHint ? `(${form.chatKeyHint})` : ''}</Badge>
          ) : (
            <Badge variant="danger">Required — AI features disabled</Badge>
          )}
        </div>
        <Input
          type="password"
          placeholder={form.hasChatKey ? 'Enter a new key to replace the stored one' : 'sk-... (required to enable AI features)'}
          value={apiKeyInput}
          onChange={e => { setApiKeyInput(e.target.value); setTestResult(null); }}
          autoComplete="off"
        />
        <div className="flex items-center gap-2 flex-wrap">
          <Button size={buttonSize} variant="outline" onClick={testConnection} disabled={testing} loading={testing} leftIcon={<Zap size={12} />}>
            Test connection
          </Button>
          {form.hasChatKey && (
            <Button size={buttonSize} variant="ghost" onClick={() => clearKey('chat')} disabled={saving}>
              Remove key
            </Button>
          )}
        </div>
        {testResult && (
          <div className={`flex items-start gap-2 text-xs ${testResult.success ? 'text-success' : 'text-danger'}`}>
            {testResult.success ? <CheckCircle2 size={14} className="mt-0.5 flex-shrink-0" /> : <XCircle size={14} className="mt-0.5 flex-shrink-0" />}
            <span>{testResult.message}</span>
          </div>
        )}
      </div>

      {size !== 'sm' && (
        <div className="rounded-lg border border-border p-3 space-y-2">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-2 text-sm font-medium text-foreground">
              <KeyRound size={14} className="text-primary" /> Embedding API Key
            </div>
            {form.hasEmbeddingKey ? (
              <Badge variant="success">Key configured {form.embeddingKeyHint ? `(${form.embeddingKeyHint})` : ''}</Badge>
            ) : sameProvider ? (
              <Badge variant="neutral">Reusing chat key (same provider)</Badge>
            ) : (
              <Badge variant="danger">Required — providers differ</Badge>
            )}
          </div>
          {embeddingKeyMissing && !sameProvider && (
            <p className="text-xs text-muted-foreground">
              Your chat and embedding providers differ, so embeddings need their own key
              (e.g. an OpenAI key for embeddings alongside an Anthropic chat key).
            </p>
          )}
          <Input
            type="password"
            placeholder={form.hasEmbeddingKey
              ? 'Enter a new key to replace the stored one'
              : sameProvider
                ? 'Optional — chat key is reused when left empty'
                : 'Required — document ingestion and search need an embedding key'}
            value={embeddingKeyInput}
            onChange={e => setEmbeddingKeyInput(e.target.value)}
            autoComplete="off"
          />
          {form.hasEmbeddingKey && (
            <Button size={buttonSize} variant="ghost" onClick={() => clearKey('embedding')} disabled={saving}>
              Remove key
            </Button>
          )}
        </div>
      )}

      <div className="flex items-center gap-3">
        <Button variant="primary" size={buttonSize} onClick={save} disabled={saving} loading={saving} leftIcon={<Save size={size === 'sm' ? 12 : 14} />}>
          Save
        </Button>
      </div>
    </div>
  );
}
