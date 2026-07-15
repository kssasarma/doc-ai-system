import { useState } from 'react';
import { Save, KeyRound, Zap, CheckCircle2, XCircle } from 'lucide-react';
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
 * Shared LLM configuration form used by both the tenant-admin Settings page and the super-admin
 * Tenants page — provider/model/routing fields plus the tenant's own (encrypted-at-rest) API key.
 * The key is write-only: the backend never returns it, only whether one is configured
 * (`hasCustomKey`) and a last-4-characters hint. Leaving the key field blank on save keeps
 * whatever is already stored; "Clear" explicitly removes it (falling back to the platform key).
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
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  const buttonSize = size === 'sm' ? 'sm' : 'md';
  const gap = size === 'sm' ? 'gap-2' : 'gap-3';

  const buildUpdate = (): TenantLLMConfigUpdate => ({
    chatProvider: form.chatProvider,
    chatModel: form.chatModel,
    embeddingProvider: form.embeddingProvider,
    embeddingModel: form.embeddingModel,
    routingEnabled: form.routingEnabled,
    simpleModel: form.simpleModel,
    complexModel: form.complexModel,
    azureEndpoint: form.azureEndpoint,
    azureDeployment: form.azureDeployment,
    // Untouched input = leave the stored key as-is; the field is never pre-filled with it.
    apiKey: apiKeyInput === '' ? undefined : apiKeyInput,
  });

  const save = async () => {
    setSaving(true);
    setTestResult(null);
    try {
      const updated = await updateTenantLLMConfig(token, tenantId, buildUpdate());
      setForm(updated);
      setApiKeyInput('');
      onSaved(updated);
      toast.success('LLM configuration saved.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to save LLM configuration.');
    } finally {
      setSaving(false);
    }
  };

  const clearKey = async () => {
    setSaving(true);
    try {
      const updated = await updateTenantLLMConfig(token, tenantId, { ...buildUpdate(), apiKey: '' });
      setForm(updated);
      setApiKeyInput('');
      onSaved(updated);
      toast.success('Custom API key removed — this tenant now uses the platform default key.');
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
              label="Embedding Provider"
              value={form.embeddingProvider}
              onChange={e => setForm(c => ({ ...c, embeddingProvider: e.target.value }))}
            />
            <Input
              label="Embedding Model"
              value={form.embeddingModel}
              onChange={e => setForm(c => ({ ...c, embeddingModel: e.target.value }))}
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
            <KeyRound size={14} className="text-primary" /> API Key
          </div>
          {form.hasCustomKey ? (
            <Badge variant="success">Custom key configured {form.keyHint ? `(${form.keyHint})` : ''}</Badge>
          ) : (
            <Badge variant="neutral">Using platform default key</Badge>
          )}
        </div>
        <Input
          type="password"
          placeholder={form.hasCustomKey ? 'Enter a new key to replace the stored one' : 'sk-... (optional — leave blank to use the platform key)'}
          value={apiKeyInput}
          onChange={e => { setApiKeyInput(e.target.value); setTestResult(null); }}
          autoComplete="off"
        />
        <div className="flex items-center gap-2 flex-wrap">
          <Button size={buttonSize} variant="outline" onClick={testConnection} disabled={testing} loading={testing} leftIcon={<Zap size={12} />}>
            Test connection
          </Button>
          {form.hasCustomKey && (
            <Button size={buttonSize} variant="ghost" onClick={clearKey} disabled={saving}>
              Remove custom key
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

      <div className="flex items-center gap-3">
        <Button variant="primary" size={buttonSize} onClick={save} disabled={saving} loading={saving} leftIcon={<Save size={size === 'sm' ? 12 : 14} />}>
          Save
        </Button>
      </div>
    </div>
  );
}
