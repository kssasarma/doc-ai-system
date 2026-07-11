import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Key, Plus, Trash2, Copy, Check, AlertTriangle, Clock, Shield } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { fetchApiKeys, createApiKey, revokeApiKey, ApiKey } from '../../services/apiKeyService';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import { Card, CardHeader, CardBody } from '../ui/Card';
import Badge, { type BadgeProps } from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import Input from '../ui/Input';
import { useToast } from '../ui/Toast';

const SCOPE_OPTIONS = ['query', 'upload', 'admin'];

const SCOPE_VARIANT: Record<string, NonNullable<BadgeProps['variant']>> = {
  query: 'primary',
  upload: 'info',
  admin: 'danger',
};

function ScopeBadge({ scope }: { scope: string }) {
  return (
    <Badge variant={SCOPE_VARIANT[scope] ?? 'neutral'} className="text-[10px] px-1.5 py-0.5">
      {scope}
    </Badge>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const toast = useToast();
  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    toast.success('Copied to clipboard');
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <IconButton label={copied ? 'Copied' : 'Copy to clipboard'} variant="ghost" size="sm" onClick={copy}>
      {copied ? <Check size={14} className="text-success" /> : <Copy size={14} />}
    </IconButton>
  );
}

export default function ApiKeysPage() {
  const navigate = useNavigate();
  const { token } = useAuth();
  const toast = useToast();
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [newKeySecret, setNewKeySecret] = useState<string | null>(null);

  // Create form
  const [name, setName] = useState('');
  const [scopes, setScopes] = useState<string[]>(['query']);
  const [rateLimit, setRateLimit] = useState(60);
  const [expirationDays, setExpirationDays] = useState<number | ''>('');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    const res = await fetchApiKeys(token);
    if (res.success && res.data) setKeys(res.data);
    setLoading(false);
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !name.trim()) return;
    setCreating(true);
    setCreateError('');
    setNewKeySecret(null);

    const res = await createApiKey(token, {
      name: name.trim(),
      scopes,
      rateLimitPerMin: rateLimit,
      expirationDays: expirationDays !== '' ? Number(expirationDays) : undefined,
    });

    if (res.success && res.data) {
      setNewKeySecret(res.data.rawKey);
      setName('');
      setScopes(['query']);
      setRateLimit(60);
      setExpirationDays('');
      await load();
    } else {
      setCreateError(res.error ?? 'Failed to create key');
    }
    setCreating(false);
  };

  const handleRevoke = async (key: ApiKey) => {
    if (!token || !window.confirm(`Revoke key "${key.name}"? This cannot be undone.`)) return;
    const res = await revokeApiKey(token, key.id);
    if (res.success) {
      toast.success(`Revoked key "${key.name}"`);
      await load();
    } else {
      toast.error(res.error ?? 'Failed to revoke key');
    }
  };

  const toggleScope = (scope: string) => {
    setScopes(prev =>
      prev.includes(scope) ? prev.filter(s => s !== scope) : [...prev, scope]
    );
  };

  return (
    <div className="min-h-screen bg-background">
      <div className="bg-surface border-b border-border px-6 py-4 flex items-center gap-4">
        <Button variant="ghost" size="sm" leftIcon={<ArrowLeft size={16} />} onClick={() => navigate(-1)}>
          Back
        </Button>
        <div className="flex items-center gap-2">
          <Key className="w-5 h-5 text-primary" />
          <h1 className="text-xl font-semibold text-foreground">API Keys</h1>
        </div>
      </div>

      <motion.div
        variants={staggerContainer}
        initial="hidden"
        animate="visible"
        className="max-w-4xl mx-auto px-6 py-8 space-y-8"
      >

        {/* Intro */}
        <motion.div variants={fadeInUp} className="bg-primary/10 border border-primary/20 rounded-xl p-5">
          <h2 className="text-sm font-semibold text-primary mb-1">Programmatic Access</h2>
          <p className="text-sm text-primary">
            API keys let you query Docs-inator from scripts, CI/CD pipelines, Slack bots, and IDE extensions.
            Authenticate by setting <code className="bg-primary/20 px-1 rounded text-xs">X-API-Key: sk-docai-…</code> or{' '}
            <code className="bg-primary/20 px-1 rounded text-xs">Authorization: ApiKey sk-docai-…</code>
          </p>
          <div className="mt-3 bg-surface rounded-lg p-3 font-mono text-xs text-foreground border border-primary/20">
            <div className="text-muted-foreground mb-1"># Example: query the API</div>
            <div>curl -X POST https://your-domain.com/api/v1/query \</div>
            <div className="pl-4">-H "X-API-Key: sk-docai-xxxxxxxxxxxx" \</div>
            <div className="pl-4">-H "Content-Type: application/json" \</div>
            <div className="pl-4">-d {`'{"question":"How do I configure LDAP?","product":"case360","version":"23.4"}'`}</div>
          </div>
        </motion.div>

        {/* New key revealed */}
        {newKeySecret && (
          <motion.div variants={fadeInUp} className="bg-success/10 border border-success/20 rounded-xl p-5">
            <div className="flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-success flex-shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-success">Key created — save it now</p>
                <p className="text-xs text-success mt-0.5 mb-3">This is the only time you will see this key. It cannot be retrieved later.</p>
                <div className="flex items-center gap-2 bg-surface rounded-lg px-3 py-2 border border-success/20 font-mono text-sm break-all">
                  <span className="flex-1 text-foreground">{newKeySecret}</span>
                  <CopyButton text={newKeySecret} />
                </div>
              </div>
            </div>
            <Button variant="link" size="sm" className="mt-3 text-success" onClick={() => setNewKeySecret(null)}>
              I've saved it, dismiss
            </Button>
          </motion.div>
        )}

        {/* Create form */}
        <motion.div variants={fadeInUp}>
          <Card>
            <CardHeader className="flex items-center gap-2">
              <Plus size={16} className="text-primary" />
              <h3 className="text-sm font-semibold text-foreground">Create New Key</h3>
            </CardHeader>
            <CardBody>
              <form onSubmit={handleCreate} className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Input
                    label="Key Name *"
                    type="text"
                    value={name}
                    onChange={e => setName(e.target.value)}
                    required
                    placeholder="e.g. Slack Bot, CI Pipeline"
                  />
                  <Input
                    label="Rate Limit (req/min)"
                    type="number"
                    value={rateLimit}
                    onChange={e => setRateLimit(Number(e.target.value))}
                    min={1}
                    max={600}
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-2">Scopes</label>
                    <div className="flex flex-wrap gap-2">
                      {SCOPE_OPTIONS.map(scope => (
                        <label key={scope} className="flex items-center gap-1.5 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={scopes.includes(scope)}
                            onChange={() => toggleScope(scope)}
                            className="rounded accent-primary"
                          />
                          <ScopeBadge scope={scope} />
                        </label>
                      ))}
                    </div>
                  </div>
                  <Input
                    label="Expiration (days, optional)"
                    type="number"
                    value={expirationDays}
                    onChange={e => setExpirationDays(e.target.value === '' ? '' : Number(e.target.value))}
                    min={1}
                    placeholder="Never"
                  />
                </div>

                {createError && (
                  <p className="text-xs text-danger bg-danger/10 rounded-lg px-3 py-2">{createError}</p>
                )}
                <Button
                  type="submit"
                  variant="primary"
                  leftIcon={<Key size={14} />}
                  loading={creating}
                  disabled={creating || !name.trim() || scopes.length === 0}
                >
                  {creating ? 'Creating…' : 'Create Key'}
                </Button>
              </form>
            </CardBody>
          </Card>
        </motion.div>

        {/* Keys list */}
        <motion.div variants={fadeInUp}>
          <Card>
            <CardHeader className="flex items-center gap-2">
              <Shield size={16} className="text-muted-foreground" />
              <h3 className="text-sm font-semibold text-foreground">Your API Keys</h3>
              <Badge variant="neutral" className="ml-auto">{keys.length} key{keys.length !== 1 ? 's' : ''}</Badge>
            </CardHeader>

            {loading ? (
              <div className="divide-y divide-border">
                <SkeletonRow columns={4} />
                <SkeletonRow columns={4} />
                <SkeletonRow columns={4} />
              </div>
            ) : keys.length === 0 ? (
              <EmptyState
                icon={Key}
                title="No API keys yet"
                description="Create one above to start querying the API programmatically."
              />
            ) : (
              <div className="divide-y divide-border">
                {keys.map(key => (
                  <div key={key.id} className={`px-5 py-4 flex items-start gap-4 ${key.revoked ? 'opacity-50' : ''}`}>
                    <Key size={16} className="text-muted-foreground mt-0.5 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-medium text-foreground">{key.name}</span>
                        {key.revoked && <Badge variant="danger">Revoked</Badge>}
                        {key.scopes.map(s => <ScopeBadge key={s} scope={s} />)}
                      </div>
                      <div className="flex items-center gap-3 mt-1 flex-wrap">
                        <code className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded font-mono">
                          {key.keyPrefix}…
                        </code>
                        <span className="text-xs text-muted-foreground">{key.rateLimitPerMin} req/min</span>
                        {key.lastUsedAt && (
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            <Clock size={10} /> Last used {new Date(key.lastUsedAt).toLocaleDateString()}
                          </span>
                        )}
                        {key.expiresAt && (
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            <Clock size={10} /> Expires {new Date(key.expiresAt).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-muted-foreground mt-0.5">
                        Created {new Date(key.createdAt).toLocaleDateString()}
                      </div>
                    </div>
                    {!key.revoked && (
                      <IconButton
                        label="Revoke key"
                        variant="danger"
                        size="sm"
                        className="flex-shrink-0"
                        onClick={() => handleRevoke(key)}
                      >
                        <Trash2 size={15} />
                      </IconButton>
                    )}
                  </div>
                ))}
              </div>
            )}
          </Card>
        </motion.div>
      </motion.div>
    </div>
  );
}
