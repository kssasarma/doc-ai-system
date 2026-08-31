import React, { useEffect, useState } from 'react';
import {
  Building2, Plus, Save, ChevronDown, ChevronUp, AlertCircle,
  CheckCircle2, XCircle, Zap, UserCog, ShieldCheck, ShieldOff, Mail,
} from 'lucide-react';
import { motion } from 'framer-motion';
import {
  listTenants, createTenant, updateTenant,
  getTenantLLMConfig, testTenantLLMConnection,
  getTenantUsers, changeUserRole,
  getRetentionPolicy, updateRetentionPolicy,
  type Tenant, type TenantLLMConfig, type DataRetentionPolicy,
} from '../../services/tenantService';
import { inviteUser } from '../../services/invitationService';
import { useAuth } from '../../context/AuthContext';
import type { TenantUser } from '../../types';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Badge from '../ui/Badge';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import EmptyState from '../ui/EmptyState';
import Spinner from '../ui/Spinner';
import { SkeletonCard } from '../ui/Skeleton';
import Input from '../ui/Input';
import Select from '../ui/Select';
import { useToast } from '../ui/Toast';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import { cn } from '../../lib/cn';

const EMPTY_FORM = { name: '', slug: '', plan: 'FREE', maxUsers: 10, maxDocuments: 100, adminEmail: '' };

type Panel = 'info' | 'llm' | 'admins' | 'retention';

export default function TenantsPage() {
  const { token } = useAuth();
  const toast = useToast();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [activePanel, setActivePanel] = useState<Record<string, Panel>>({});
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    setLoadError('');
    try {
      const data = await listTenants(token);
      setTenants(data);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Failed to load tenants');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !form.name || !form.slug || !form.adminEmail) return;
    setCreating(true);
    try {
      const t = await createTenant(token, form);
      setTenants(prev => [...prev, t]);
      setForm(EMPTY_FORM);
      toast.success(`Tenant "${t.name}" created. An invitation was emailed to ${form.adminEmail}.`);
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to create tenant.');
    } finally {
      setCreating(false);
    }
  };

  const toggle = (id: string) => {
    setExpandedId(prev => prev === id ? null : id);
    setActivePanel(prev => ({ ...prev, [id]: prev[id] ?? 'info' }));
  };

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      <PageHeader title="Tenant Management" description="Create tenants and manage isolation, LLM provider config, and data retention." />

      {/* Create tenant */}
      <motion.div variants={fadeInUp} className="bg-primary/10 border border-primary/20 rounded-xl p-4">
        <div className="flex items-center gap-2 mb-3 text-sm font-medium text-primary">
          <Plus size={14} /> Create new tenant
        </div>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input id="tenant-name" label="Name *" placeholder="Acme Corp" value={form.name}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
          <Input id="tenant-slug" label="Slug *" placeholder="acme-corp" value={form.slug}
            onChange={e => setForm(f => ({ ...f, slug: e.target.value }))} required />
          <div className="sm:col-span-2">
            <Input id="tenant-admin-email" type="email" label="Admin email *" placeholder="admin@acme.com" value={form.adminEmail}
              onChange={e => setForm(f => ({ ...f, adminEmail: e.target.value }))} required
              hint="This person is invited as the tenant's first admin." />
          </div>
          <Select id="tenant-plan" label="Plan" value={form.plan} onChange={e => setForm(f => ({ ...f, plan: e.target.value }))}>
            <option>FREE</option><option>PRO</option><option>ENTERPRISE</option>
          </Select>
          <Input id="tenant-max-users" type="number" label="Max users" min={1} value={form.maxUsers}
            onChange={e => setForm(f => ({ ...f, maxUsers: +e.target.value }))} />
          <Button type="submit" variant="primary" disabled={creating} loading={creating}
            leftIcon={<Plus size={14} />} className="sm:col-span-2">
            Create & invite admin
          </Button>
        </form>
      </motion.div>

      {loading ? (
        <div className="space-y-3">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      ) : loadError ? (
        <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{loadError}</div>
      ) : tenants.length === 0 ? (
        <Card>
          <EmptyState icon={Building2} title="No tenants yet" description="Create the first one above." />
        </Card>
      ) : (
        <motion.div variants={fadeInUp} className="space-y-3">
          {tenants.map(t => (
            <TenantCard key={t.id} tenant={t} token={token!}
              expanded={expandedId === t.id}
              panel={activePanel[t.id] ?? 'info'}
              onToggle={() => toggle(t.id)}
              onPanelChange={p => setActivePanel(prev => ({ ...prev, [t.id]: p }))}
              onUpdated={updated => setTenants(prev => prev.map(x => x.id === updated.id ? updated : x))}
            />
          ))}
        </motion.div>
      )}
    </motion.div>
  );
}

function TenantCard({ tenant, token, expanded, panel, onToggle, onPanelChange, onUpdated }: {
  tenant: Tenant; token: string; expanded: boolean;
  panel: Panel;
  onToggle: () => void;
  onPanelChange: (p: Panel) => void;
  onUpdated: (t: Tenant) => void;
}) {
  const toast = useToast();
  const [llmConfig, setLlmConfig] = useState<TenantLLMConfig | null>(null);
  const [retention, setRetention] = useState<DataRetentionPolicy | null>(null);
  const [saving, setSaving] = useState(false);
  const [togglingActive, setTogglingActive] = useState(false);

  useEffect(() => {
    if (!expanded) return;
    if (panel === 'llm' && !llmConfig) {
      getTenantLLMConfig(token, tenant.id).then(setLlmConfig).catch(() => {});
    }
    if (panel === 'retention' && !retention) {
      getRetentionPolicy(token, tenant.id).then(setRetention).catch(() => {});
    }
  }, [expanded, panel]);

  const saveRetention = async () => {
    if (!retention) return;
    setSaving(true);
    try {
      const updated = await updateRetentionPolicy(token, tenant.id, retention);
      setRetention(updated);
      toast.success('Retention policy saved.');
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async () => {
    setTogglingActive(true);
    try {
      const updated = await updateTenant(token, tenant.id, {
        name: tenant.name, plan: tenant.plan, active: !tenant.active,
        maxUsers: tenant.maxUsers, maxDocuments: tenant.maxDocuments,
      });
      onUpdated(updated);
      toast.success(updated.active ? `${updated.name} activated.` : `${updated.name} deactivated.`);
    } finally {
      setTogglingActive(false);
    }
  };

  const planBadge: Record<string, 'neutral' | 'primary' | 'info'> = {
    FREE: 'neutral',
    PRO: 'primary',
    ENTERPRISE: 'info',
  };

  const PANELS: { key: Panel; label: string }[] = [
    { key: 'info', label: 'Info' },
    { key: 'llm', label: 'LLM Config' },
    { key: 'admins', label: 'Admins' },
    { key: 'retention', label: 'Data Retention' },
  ];

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-3 p-4">
        <Building2 size={16} className="text-primary flex-shrink-0" />
        <button onClick={onToggle} className="flex-1 text-left min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-foreground">{tenant.name}</span>
            <span className="text-xs text-muted-foreground">/{tenant.slug}</span>
            <Badge variant={planBadge[tenant.plan] ?? 'neutral'}>{tenant.plan}</Badge>
            {!tenant.active && <Badge variant="danger">Inactive</Badge>}
          </div>
          <div className="text-xs text-muted-foreground mt-0.5">
            {tenant.maxUsers} users · {tenant.documentCount ?? 0} / {tenant.maxDocuments} docs
          </div>
        </button>
        <Button
          variant={tenant.active ? 'danger' : 'outline'}
          size="sm"
          onClick={toggleActive}
          disabled={togglingActive}
          loading={togglingActive}
        >
          {tenant.active ? 'Deactivate' : 'Activate'}
        </Button>
        <IconButton
          label={expanded ? 'Collapse tenant details' : 'Expand tenant details'}
          variant="ghost"
          size="sm"
          onClick={onToggle}
        >
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </IconButton>
      </div>

      {expanded && (
        <div className="border-t border-border">
          <div className="flex border-b border-border overflow-x-auto">
            {PANELS.map(({ key, label }) => (
              <button key={key} onClick={() => onPanelChange(key)}
                className={cn(
                  'px-4 py-2 text-xs font-medium transition-colors whitespace-nowrap',
                  panel === key ? 'border-b-2 border-primary text-primary' : 'text-muted-foreground hover:text-foreground',
                )}>
                {label}
              </button>
            ))}
          </div>

          <div className="p-4">
            {panel === 'info' && (
              <InfoPanel tenant={tenant} token={token} onUpdated={onUpdated} />
            )}

            {panel === 'llm' && (
              <LlmReadOnlyPanel token={token} tenantId={tenant.id} config={llmConfig} />
            )}

            {panel === 'admins' && (
              <AdminsPanel token={token} tenant={tenant} />
            )}

            {panel === 'retention' && (
              <div className="space-y-3">
                {!retention ? (
                  <div className="text-center py-4"><Spinner size="md" /></div>
                ) : (
                  <>
                    <p className="text-xs text-muted-foreground">Configure how long data is retained before automatic deletion.</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {([
                        { key: 'queryLogDays', label: 'Query logs (days)' },
                        { key: 'chatSessionDays', label: 'Chat sessions (days)' },
                        { key: 'auditLogDays', label: 'Audit logs (days)' },
                        { key: 'feedbackDays', label: 'Feedback (days)' },
                      ] as const).map(({ key, label }) => (
                        <Input key={key} type="number" label={label}
                          value={retention[key]}
                          onChange={e => setRetention(r => r ? { ...r, [key]: +e.target.value } : r)} />
                      ))}
                    </div>
                    <Button variant="primary" size="sm" onClick={saveRetention} disabled={saving} loading={saving} leftIcon={<Save size={12} />}>
                      Save Policy
                    </Button>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}

// ─── Info panel: read-only metadata + editable tier/limits ──────────────────

function InfoPanel({ tenant, token, onUpdated }: {
  tenant: Tenant; token: string; onUpdated: (t: Tenant) => void;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ plan: tenant.plan, maxUsers: tenant.maxUsers, maxDocuments: tenant.maxDocuments });
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    try {
      const updated = await updateTenant(token, tenant.id, {
        name: tenant.name,
        plan: form.plan,
        active: tenant.active,
        maxUsers: form.maxUsers,
        maxDocuments: form.maxDocuments,
      });
      onUpdated(updated);
      setEditing(false);
      toast.success('Tenant settings saved.');
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to save tenant settings.');
    } finally {
      setSaving(false);
    }
  };

  const cancel = () => {
    setForm({ plan: tenant.plan, maxUsers: tenant.maxUsers, maxDocuments: tenant.maxDocuments });
    setEditing(false);
  };

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-muted-foreground">
        <div><span className="font-medium text-foreground">ID:</span> <span className="font-mono text-xs">{tenant.id}</span></div>
        <div><span className="font-medium text-foreground">OIDC:</span> {tenant.oidcEnabled ? `Enabled (${tenant.oidcProvider})` : 'Disabled'}</div>
        <div><span className="font-medium text-foreground">Created:</span> {new Date(tenant.createdAt).toLocaleDateString()}</div>
        <div><span className="font-medium text-foreground">Updated:</span> {new Date(tenant.updatedAt).toLocaleDateString()}</div>
      </div>

      <div className="border-t border-border pt-4">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-medium text-foreground">Tier &amp; limits</span>
          {!editing && (
            <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
              Edit
            </Button>
          )}
        </div>

        {editing ? (
          <div className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Select label="Plan / tier" value={form.plan} onChange={e => setForm(f => ({ ...f, plan: e.target.value }))}>
                <option value="FREE">FREE</option>
                <option value="PRO">PRO</option>
                <option value="ENTERPRISE">ENTERPRISE</option>
              </Select>
              <Input type="number" label="Max users" min={1} value={form.maxUsers}
                onChange={e => setForm(f => ({ ...f, maxUsers: +e.target.value }))} />
              <Input type="number" label="Max documents" min={1} value={form.maxDocuments}
                onChange={e => setForm(f => ({ ...f, maxDocuments: +e.target.value }))} />
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="primary" onClick={save} disabled={saving} loading={saving} leftIcon={<Save size={12} />}>
                Save
              </Button>
              <Button size="sm" variant="ghost" onClick={cancel} disabled={saving}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm text-muted-foreground">
            <div><span className="font-medium text-foreground">Plan:</span> {tenant.plan}</div>
            <div><span className="font-medium text-foreground">Max users:</span> {tenant.maxUsers}</div>
            <div>
              <span className="font-medium text-foreground">Docs:</span>{' '}
              {tenant.documentCount ?? 0} / {tenant.maxDocuments}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── LLM read-only panel with test connection ────────────────────────────────

function LlmReadOnlyPanel({ token, tenantId, config }: {
  token: string; tenantId: string; config: TenantLLMConfig | null;
}) {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  const testConnection = async () => {
    if (!config) return;
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testTenantLLMConnection(token, tenantId, {
        provider: config.chatProvider,
        model: config.chatModel,
        baseUrl: config.chatBaseUrl || undefined,
      });
      setTestResult(result);
    } catch (e) {
      setTestResult({ success: false, message: e instanceof Error ? e.message : 'Test failed.' });
    } finally {
      setTesting(false);
    }
  };

  if (!config) {
    return <div className="text-center py-4"><Spinner size="md" /></div>;
  }

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">
        LLM configuration is managed by the tenant's admin. As super admin you can view these settings and test the connection.
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <LlmField label="Chat provider" value={config.chatProvider} />
        <LlmField label="Chat model" value={config.chatModel} />
        <LlmField label="Chat base URL" value={config.chatBaseUrl || '(provider default)'} mono />
        <LlmField label="Embedding provider" value={config.embeddingProvider} />
        <LlmField label="Embedding model" value={config.embeddingModel} />
        <LlmField label="Embedding base URL" value={config.embeddingBaseUrl || '(provider default)'} mono />
        <LlmField label="Temperature" value={String(config.temperature)} />
        <LlmField label="Max tokens" value={String(config.maxTokens)} />
        {config.routingEnabled && (
          <>
            <LlmField label="Simple model" value={config.simpleModel} />
            <LlmField label="Complex model" value={config.complexModel} />
          </>
        )}
        {config.rerankModel && <LlmField label="Re-rank model" value={config.rerankModel} />}
        <div className="col-span-full flex gap-3 flex-wrap items-center text-xs text-muted-foreground">
          <span>
            Chat key:{' '}
            {config.hasChatKey
              ? <span className="text-success font-medium">Configured {config.chatKeyHint ? `(${config.chatKeyHint})` : ''}</span>
              : <span className="text-danger font-medium">Not set</span>}
          </span>
          <span>
            Embedding key:{' '}
            {config.hasEmbeddingKey
              ? <span className="text-success font-medium">Configured {config.embeddingKeyHint ? `(${config.embeddingKeyHint})` : ''}</span>
              : config.embeddingProvider === config.chatProvider
                ? <span className="text-muted-foreground">Reusing chat key</span>
                : <span className="text-danger font-medium">Not set</span>}
          </span>
          {config.routingEnabled && <span className="text-primary font-medium">Smart routing enabled</span>}
        </div>
      </div>

      <div className="flex items-center gap-3 flex-wrap pt-1">
        <Button
          size="sm" variant="outline"
          onClick={testConnection}
          disabled={testing || !config.hasChatKey}
          loading={testing}
          leftIcon={<Zap size={12} />}
        >
          Test connection
        </Button>
        {!config.hasChatKey && (
          <span className="text-xs text-muted-foreground">No API key configured — test unavailable</span>
        )}
      </div>

      {testResult && (
        <div className={cn('flex items-start gap-2 text-xs rounded-lg p-2.5 border', testResult.success
          ? 'text-success border-success/30 bg-success/10'
          : 'text-danger border-danger/30 bg-danger/10')}>
          {testResult.success
            ? <CheckCircle2 size={14} className="mt-0.5 flex-shrink-0" />
            : <XCircle size={14} className="mt-0.5 flex-shrink-0" />}
          <span>{testResult.message}</span>
        </div>
      )}
    </div>
  );
}

function LlmField({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className={cn('text-sm text-foreground', mono && 'font-mono text-xs break-all')}>{value}</span>
    </div>
  );
}

// ─── Admins panel: list admins, promote/demote, invite ───────────────────────

function AdminsPanel({ token, tenant }: { token: string; tenant: Tenant }) {
  const toast = useToast();
  const [users, setUsers] = useState<TenantUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviting, setInviting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  useEffect(() => { loadUsers(); }, [tenant.id]);

  const loadUsers = async () => {
    setLoading(true);
    setLoadError('');
    try {
      const page = await getTenantUsers(token, tenant.id, { size: 100 });
      setUsers(page.content);
    } catch {
      setLoadError('Failed to load users.');
    } finally {
      setLoading(false);
    }
  };

  const toggleRole = async (user: TenantUser) => {
    const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    setTogglingId(user.userId.toString());
    try {
      await changeUserRole(token, user.userId.toString(), newRole);
      setUsers(prev => prev.map(u => u.userId === user.userId ? { ...u, role: newRole } : u));
      toast.success(`${user.email} is now ${newRole}.`);
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || `Failed to change role.`);
    } finally {
      setTogglingId(null);
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) return;
    setInviting(true);
    try {
      await inviteUser(token, inviteEmail.trim(), tenant.id);
      toast.success(`Invitation sent to ${inviteEmail} as admin.`);
      setInviteEmail('');
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to send invitation.');
    } finally {
      setInviting(false);
    }
  };

  const admins = users.filter(u => u.role === 'ADMIN');
  const regularUsers = users.filter(u => u.role !== 'ADMIN');

  return (
    <div className="space-y-5">
      {/* Invite new admin */}
      <div className="rounded-lg border border-border p-3 space-y-2">
        <div className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Mail size={14} className="text-primary" /> Invite new admin
        </div>
        <p className="text-xs text-muted-foreground">
          Send an invitation email to add a new admin to this tenant.
        </p>
        <form onSubmit={handleInvite} className="flex gap-2 items-end">
          <div className="flex-1">
            <Input
              type="email"
              placeholder="admin@example.com"
              value={inviteEmail}
              onChange={e => setInviteEmail(e.target.value)}
            />
          </div>
          <Button type="submit" size="sm" variant="primary" disabled={inviting || !inviteEmail.trim()} loading={inviting}>
            Invite
          </Button>
        </form>
      </div>

      {/* User list */}
      {loading ? (
        <div className="text-center py-4"><Spinner size="md" /></div>
      ) : loadError ? (
        <div className="flex items-center gap-2 text-sm text-danger"><AlertCircle size={14} />{loadError}</div>
      ) : (
        <div className="space-y-4">
          {/* Admins section */}
          <div>
            <div className="flex items-center gap-2 mb-2">
              <UserCog size={14} className="text-primary" />
              <span className="text-xs font-semibold text-foreground uppercase tracking-wider">
                Admins ({admins.length})
              </span>
            </div>
            {admins.length === 0 ? (
              <p className="text-xs text-muted-foreground pl-5">No admins yet — invite one above.</p>
            ) : (
              <div className="space-y-1">
                {admins.map(u => (
                  <UserRow key={u.userId.toString()} user={u} toggling={togglingId === u.userId.toString()}
                    onToggle={() => toggleRole(u)} actionLabel="Demote to user" actionIcon={<ShieldOff size={12} />}
                    actionVariant="ghost" />
                ))}
              </div>
            )}
          </div>

          {/* Regular users section */}
          {regularUsers.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-semibold text-foreground uppercase tracking-wider">
                  Users ({regularUsers.length})
                </span>
              </div>
              <div className="space-y-1">
                {regularUsers.map(u => (
                  <UserRow key={u.userId.toString()} user={u} toggling={togglingId === u.userId.toString()}
                    onToggle={() => toggleRole(u)} actionLabel="Promote to admin" actionIcon={<ShieldCheck size={12} />}
                    actionVariant="outline" />
                ))}
              </div>
            </div>
          )}

          {users.length === 0 && (
            <p className="text-xs text-muted-foreground text-center py-2">No users in this tenant yet.</p>
          )}
        </div>
      )}
    </div>
  );
}

function UserRow({ user, toggling, onToggle, actionLabel, actionIcon, actionVariant }: {
  user: TenantUser;
  toggling: boolean;
  onToggle: () => void;
  actionLabel: string;
  actionIcon: React.ReactNode;
  actionVariant: 'outline' | 'ghost';
}) {
  return (
    <div className="flex items-center gap-2 py-1.5 px-2 rounded-lg hover:bg-muted/50 transition-colors">
      <div className="flex-1 min-w-0">
        <span className="text-sm text-foreground truncate block">{user.email}</span>
        {user.displayName && (
          <span className="text-xs text-muted-foreground truncate block">{user.displayName}</span>
        )}
      </div>
      {!user.active && <Badge variant="neutral">Inactive</Badge>}
      <Button
        size="sm"
        variant={actionVariant}
        onClick={onToggle}
        disabled={toggling}
        loading={toggling}
        leftIcon={actionIcon}
      >
        {actionLabel}
      </Button>
    </div>
  );
}
