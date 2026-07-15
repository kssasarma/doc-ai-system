import { useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, Settings, SlidersHorizontal } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import ThemeToggle from './ThemeToggle';
import Menu from './Menu';

/** The one place account-level actions live — identity, Preferences, admin console entry (when
 * applicable), sign out. Meant to sit in a persistent top-right corner across every authenticated
 * page (chat, the standalone feature pages, and the admin console), so it's always in the same
 * spot regardless of which part of the app you're in.
 *
 * `compact`: icon-only trigger (just the avatar circle, no name/role/chevron) for tight spaces —
 * e.g. the admin console's fixed-width sidebar header, which already shows identity elsewhere and
 * has a "Back to Chat" link competing for the same row. */
export default function AccountMenu({
  onOpenPreferences, compact = false,
}: {
  onOpenPreferences: () => void;
  compact?: boolean;
}) {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  return (
    <div className="flex items-center gap-1.5">
      <ThemeToggle />
      <Menu
        align="end"
        trigger={
          compact ? (
            <button
              type="button"
              aria-label={`Account menu (${user.username})`}
              title={user.username}
              className="w-7 h-7 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-xs font-bold flex-shrink-0 hover:opacity-90 transition-opacity"
            >
              {user.username.charAt(0).toUpperCase()}
            </button>
          ) : (
            <button
              type="button"
              className="flex items-center gap-2 pl-1.5 pr-2 py-1 rounded-lg hover:bg-surface-hover transition-colors"
            >
              <div className="w-7 h-7 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-xs font-bold flex-shrink-0">
                {user.username.charAt(0).toUpperCase()}
              </div>
              <div className="hidden sm:block text-left leading-tight">
                <div className="text-sm font-medium text-foreground">{user.username}</div>
                <div className="text-xs text-muted-foreground">{user.role}</div>
              </div>
              <ChevronDown size={14} className="text-muted-foreground flex-shrink-0" />
            </button>
          )
        }
        options={[
          { key: 'preferences', label: 'Preferences', icon: <SlidersHorizontal size={14} />, onSelect: onOpenPreferences },
          ...(isAdmin
            ? [{ key: 'admin', label: 'Admin Panel', icon: <Settings size={14} />, onSelect: () => navigate('/admin') }]
            : []),
          { key: 'signout', label: 'Sign out', icon: <LogOut size={14} />, onSelect: logout, danger: true },
        ]}
      />
    </div>
  );
}
