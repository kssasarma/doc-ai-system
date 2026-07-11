import React, { useEffect, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronsUpDown, Check, Loader2 } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { listMyTenants, switchTenant } from '../../services/authService';
import type { TenantMembership } from '../../types';
import { scaleIn } from '../../lib/motion';

/** Slack-workspace-style switcher — only renders once we know the caller belongs to more than
 * one tenant, so single-tenant users (the overwhelming majority) see nothing extra. */
export default function TenantSwitcher({ isCollapsed }: { isCollapsed: boolean }) {
  const { user, token, applySession } = useAuth();
  const [memberships, setMemberships] = useState<TenantMembership[] | null>(null);
  const [open, setOpen] = useState(false);
  const [switching, setSwitching] = useState<string | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token || !user || user.role === 'SUPER_ADMIN') return;
    listMyTenants(token).then(setMemberships).catch(() => setMemberships([]));
  }, [token, user]);

  const handleSwitch = useCallback(async (tenantId: string) => {
    if (!token || tenantId === user?.tenantId) { setOpen(false); return; }
    setSwitching(tenantId);
    setError('');
    try {
      const data = await switchTenant(token, tenantId);
      if (data.error || !data.token) throw new Error(data.error || 'Switch failed');
      applySession(data);
      // Full reload: many tenant-scoped views fetch data on mount tied to the previous tenant.
      window.location.href = '/';
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to switch tenant');
      setSwitching(null);
    }
  }, [token, user?.tenantId, applySession]);

  if (!memberships || memberships.length < 2) return null;

  const active = memberships.find(m => m.tenantId === user?.tenantId);

  if (isCollapsed) {
    return (
      <button
        onClick={() => setOpen(v => !v)}
        title={active ? `${active.tenantName} — click to switch workspace` : 'Switch workspace'}
        aria-label={active ? `${active.tenantName} — click to switch workspace` : 'Switch workspace'}
        className="w-9 h-9 rounded-lg bg-muted hover:bg-surface-hover flex items-center justify-center text-xs font-bold text-muted-foreground transition-colors"
      >
        {(active?.tenantName ?? '?').charAt(0).toUpperCase()}
      </button>
    );
  }

  return (
    <div className="relative mb-2">
      <button
        onClick={() => setOpen(v => !v)}
        className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface-hover transition-colors text-left"
      >
        <div className="w-6 h-6 rounded bg-primary text-primary-foreground flex items-center justify-center text-[10px] font-bold flex-shrink-0">
          {(active?.tenantName ?? '?').charAt(0).toUpperCase()}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-xs font-medium text-foreground truncate">{active?.tenantName ?? 'Unknown workspace'}</div>
        </div>
        <ChevronsUpDown size={13} className="text-muted-foreground flex-shrink-0" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            variants={scaleIn}
            initial="hidden"
            animate="visible"
            exit="exit"
            className="absolute bottom-full left-0 mb-1 w-full bg-surface border border-border rounded-lg shadow-elevated dark:shadow-elevated-dark overflow-hidden z-20"
          >
            {memberships.map(m => (
              <button
                key={m.tenantId}
                onClick={() => handleSwitch(m.tenantId)}
                disabled={switching !== null}
                className="w-full flex items-center gap-2 px-3 py-2 text-left text-xs hover:bg-surface-hover transition-colors disabled:opacity-60"
              >
                <div className="w-5 h-5 rounded bg-primary text-primary-foreground flex items-center justify-center text-[9px] font-bold flex-shrink-0">
                  {m.tenantName.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-foreground truncate">{m.tenantName}</div>
                  <div className="text-muted-foreground">{m.role}</div>
                </div>
                {switching === m.tenantId ? (
                  <Loader2 size={12} className="animate-spin text-muted-foreground flex-shrink-0" />
                ) : m.tenantId === user?.tenantId ? (
                  <Check size={12} className="text-success flex-shrink-0" />
                ) : null}
              </button>
            ))}
            {error && <div className="px-3 py-2 text-[11px] text-danger">{error}</div>}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
