import { useEffect, useState } from 'react';
import { Command } from 'cmdk';
import { useNavigate } from 'react-router-dom';
import {
  MessageSquarePlus, Bookmark, Folder, BookOpen, Bell, Key, Settings,
  BarChart2, FileText, Map, TrendingUp, DollarSign, Users, UsersRound, Shield,
  HelpCircle, MessageSquare, AlertTriangle, Lock, ShieldAlert, Sun, Moon, LogOut, Building2,
  Library, Search,
  type LucideIcon,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { fetchChatSessions } from '../../services/chatService';
import { listMyTenants, switchTenant } from '../../services/authService';
import type { BackendSession, TenantMembership } from '../../types';

const ADMIN_PAGES = [
  { to: '/admin/overview', label: 'Overview', icon: BarChart2 },
  { to: '/admin/documents', label: 'Documents', icon: FileText },
  { to: '/admin/coverage', label: 'Coverage', icon: Map },
  { to: '/admin/query-intelligence', label: 'Query Intelligence', icon: TrendingUp },
  { to: '/admin/faq', label: 'FAQ Review', icon: MessageSquare },
  { to: '/admin/gap-reports', label: 'Gap Reports', icon: AlertTriangle },
  { to: '/admin/pii-flags', label: 'PII Review', icon: ShieldAlert },
  { to: '/admin/cost', label: 'Cost', icon: DollarSign },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/groups', label: 'Groups', icon: UsersRound },
  { to: '/admin/audit-log', label: 'Audit Log', icon: Shield },
  { to: '/admin/escalations', label: 'Escalations', icon: HelpCircle },
  { to: '/admin/gdpr', label: 'GDPR', icon: Lock },
  { to: '/admin/settings', label: 'Settings', icon: Settings },
];

export default function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const { user, token, isAdmin, logout, applySession } = useAuth();
  const { resolvedTheme, toggle: toggleTheme } = useTheme();
  const [recentSessions, setRecentSessions] = useState<BackendSession[]>([]);
  const [memberships, setMemberships] = useState<TenantMembership[]>([]);
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (!open) return;
    setSearch('');
  }, [open]);

  useEffect(() => {
    if (!open || !token) return;
    fetchChatSessions(token).then(res => {
      if (res.success && res.data) {
        setRecentSessions([...res.data.sessions].sort((a, b) => b.lastActiveAt.localeCompare(a.lastActiveAt)).slice(0, 6));
      }
    });
    if (user?.role !== 'SUPER_ADMIN') {
      listMyTenants(token).then(setMemberships).catch(() => {});
    }
  }, [open, token, user?.role]);

  const go = (to: string) => {
    navigate(to);
    onClose();
  };

  const handleSwitchTenant = async (tenantId: string) => {
    if (!token) return;
    const data = await switchTenant(token, tenantId);
    if (data.token) {
      applySession(data);
      window.location.href = '/';
    }
  };

  return (
    <Command.Dialog
      open={open}
      onOpenChange={v => !v && onClose()}
      label="Command palette"
      className="fixed inset-0 z-[200] flex items-start justify-center pt-[15vh] px-4"
      shouldFilter
    >
      <div className="fixed inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} aria-hidden />
      <div className="relative w-full max-w-lg bg-surface border border-border rounded-2xl shadow-elevated dark:shadow-elevated-dark overflow-hidden animate-scale-in">
        <Command.Input
          autoFocus
          value={search}
          onValueChange={setSearch}
          placeholder="Type a command or search…"
          className="w-full px-4 py-3.5 text-sm bg-transparent border-b border-border text-foreground placeholder:text-muted-foreground focus:outline-none"
        />
        <Command.List className="max-h-96 overflow-y-auto p-2">
          <Command.Empty className="py-8 text-center text-sm text-muted-foreground">No results found.</Command.Empty>

          {search.trim() && (
            <Command.Group heading="Search" className="px-2 py-1.5 text-xs font-medium text-muted-foreground [&_[cmdk-group-heading]]:mb-1">
              <PaletteItem
                icon={Search}
                label={`Search docs: "${search.trim()}"`}
                onSelect={() => go(`/library?q=${encodeURIComponent(search.trim())}`)}
              />
            </Command.Group>
          )}

          <Command.Group heading="Actions" className="px-2 py-1.5 text-xs font-medium text-muted-foreground [&_[cmdk-group-heading]]:mb-1">
            <PaletteItem icon={MessageSquarePlus} label="New chat" onSelect={() => go('/')} />
            <PaletteItem icon={Library} label="Library" onSelect={() => go('/library')} />
            <PaletteItem icon={Bookmark} label="Bookmarks" onSelect={() => go('/bookmarks')} />
            <PaletteItem icon={Folder} label="Collections" onSelect={() => go('/collections')} />
            <PaletteItem icon={BookOpen} label="FAQ" onSelect={() => go('/faq')} />
            <PaletteItem icon={Bell} label="Subscriptions" onSelect={() => go('/subscriptions')} />
            <PaletteItem icon={Key} label="API Keys" onSelect={() => go('/api-keys')} />
            <PaletteItem
              icon={resolvedTheme === 'dark' ? Sun : Moon}
              label={resolvedTheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
              onSelect={() => { toggleTheme(); onClose(); }}
            />
            <PaletteItem icon={LogOut} label="Sign out" onSelect={() => { logout(); onClose(); }} />
          </Command.Group>

          {recentSessions.length > 0 && (
            <Command.Group heading="Recent chats" className="px-2 py-1.5 text-xs font-medium text-muted-foreground [&_[cmdk-group-heading]]:mb-1">
              {recentSessions.map(s => (
                <PaletteItem
                  key={s.chatId}
                  icon={MessageSquarePlus}
                  label={s.title || `Chat ${s.chatId.slice(0, 8)}`}
                  onSelect={() => go(`/chat/${s.chatId}`)}
                />
              ))}
            </Command.Group>
          )}

          {isAdmin && (
            <Command.Group heading="Admin" className="px-2 py-1.5 text-xs font-medium text-muted-foreground [&_[cmdk-group-heading]]:mb-1">
              {ADMIN_PAGES.map(p => (
                <PaletteItem key={p.to} icon={p.icon} label={p.label} onSelect={() => go(p.to)} />
              ))}
            </Command.Group>
          )}

          {memberships.length > 1 && (
            <Command.Group heading="Switch workspace" className="px-2 py-1.5 text-xs font-medium text-muted-foreground [&_[cmdk-group-heading]]:mb-1">
              {memberships.map(m => (
                <PaletteItem
                  key={m.tenantId}
                  icon={Building2}
                  label={m.tenantName}
                  onSelect={() => handleSwitchTenant(m.tenantId)}
                />
              ))}
            </Command.Group>
          )}
        </Command.List>
      </div>
    </Command.Dialog>
  );
}

function PaletteItem({ icon: Icon, label, onSelect }: { icon: LucideIcon; label: string; onSelect: () => void }) {
  return (
    <Command.Item
      onSelect={onSelect}
      className="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-foreground cursor-pointer aria-selected:bg-primary/10 aria-selected:text-primary transition-colors"
    >
      <Icon size={15} className="flex-shrink-0" />
      {label}
    </Command.Item>
  );
}
