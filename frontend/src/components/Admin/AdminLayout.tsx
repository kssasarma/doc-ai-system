import { Suspense, lazy, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, LucideIcon } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import AccountMenu from '../ui/AccountMenu';
import Spinner from '../ui/Spinner';
import { cn } from '../../lib/cn';

const PreferencesModal = lazy(() => import('../Settings/PreferencesModal'));
const TenantSwitcher = lazy(() => import('../Sidebar/TenantSwitcher'));

export interface AdminNavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

function TabFallback() {
  return (
    <div className="p-12 text-center text-muted-foreground flex flex-col items-center gap-2">
      <Spinner size="md" />
      Loading…
    </div>
  );
}

/** One shared `layoutId` animates a pill/underline sliding between nav items on route change —
 * used for both the desktop sidebar (pill fill) and mobile tab bar (underline), so both surfaces
 * express "which item is active" with the same animated language instead of two static styles. */
function NavItem({
  item, isActive, variant,
}: {
  item: AdminNavItem;
  isActive: boolean;
  variant: 'sidebar' | 'tab';
}) {
  return (
    <NavLink
      to={item.to}
      end
      className={cn(
        'relative flex items-center whitespace-nowrap transition-colors font-medium',
        variant === 'sidebar'
          ? cn('gap-2.5 px-3 py-2 rounded-lg text-sm', isActive ? 'text-primary' : 'text-muted-foreground hover:text-foreground')
          : cn('gap-1.5 px-4 py-3 text-sm', isActive ? 'text-primary' : 'text-muted-foreground hover:text-foreground'),
      )}
    >
      {isActive && (
        <motion.span
          layoutId={variant === 'sidebar' ? 'admin-nav-active-sidebar' : 'admin-nav-active-tab'}
          transition={{ type: 'spring', stiffness: 380, damping: 32 }}
          className={cn(
            'absolute',
            variant === 'sidebar' ? 'inset-0 bg-primary/10 rounded-lg' : 'inset-x-0 bottom-0 h-0.5 bg-primary',
          )}
        />
      )}
      <item.icon size={variant === 'sidebar' ? 16 : 14} className="relative" />
      <span className="relative">{item.label}</span>
    </NavLink>
  );
}

export default function AdminLayout({ navItems, title }: { navItems: AdminNavItem[]; title: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, isSuperAdmin } = useAuth();
  const [prefsOpen, setPrefsOpen] = useState(false);

  const isItemActive = (to: string) =>
    location.pathname === to || location.pathname === `${to}/` || location.pathname.startsWith(`${to}/`);

  const activeItem = navItems.find(item => isItemActive(item.to));
  useDocumentTitle(activeItem?.label ?? title);

  return (
    <div className="min-h-screen bg-background flex flex-col md:flex-row">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:flex-col md:w-60 md:flex-shrink-0 bg-surface border-r border-border">
        <div className="px-4 py-4 border-b border-border">
          <div className="flex items-center justify-between mb-3">
            <button
              onClick={() => navigate('/')}
              className="flex items-center gap-2 text-muted-foreground hover:text-foreground text-sm transition-colors"
            >
              <ArrowLeft className="w-4 h-4" /> Back to Chat
            </button>
            <AccountMenu onOpenPreferences={() => setPrefsOpen(true)} compact />
          </div>
          <h1 className="text-lg font-semibold text-foreground">{title}</h1>
          {user && (
            <p className="text-xs text-muted-foreground mt-1 truncate">
              {user.username} · {isSuperAdmin ? 'Super Admin' : 'Tenant Admin'}
            </p>
          )}
        </div>
        {!isSuperAdmin && (
          <div className="px-3 pt-3">
            <Suspense fallback={null}>
              <TenantSwitcher isCollapsed={false} menuPlacement="down" />
            </Suspense>
          </div>
        )}
        <nav className="flex-1 overflow-y-auto p-3 space-y-1">
          {navItems.map(item => (
            <NavItem key={item.to} item={item} isActive={isItemActive(item.to)} variant="sidebar" />
          ))}
        </nav>
      </aside>

      {/* Mobile top bar */}
      <div className="md:hidden bg-surface border-b border-border flex-shrink-0">
        <div className="px-4 py-3 flex items-center justify-between gap-3">
          <button onClick={() => navigate('/')} className="flex items-center gap-1.5 text-muted-foreground text-sm">
            <ArrowLeft className="w-4 h-4" /> Chat
          </button>
          <h1 className="text-base font-semibold text-foreground">{title}</h1>
          <div className="flex items-center gap-2">
            {!isSuperAdmin && (
              <Suspense fallback={null}>
                <TenantSwitcher isCollapsed menuPlacement="down" />
              </Suspense>
            )}
            <AccountMenu onOpenPreferences={() => setPrefsOpen(true)} compact />
          </div>
        </div>
        <div className="overflow-x-auto px-2">
          <div className="flex gap-0 min-w-max">
            {navItems.map(item => (
              <NavItem key={item.to} item={item} isActive={isItemActive(item.to)} variant="tab" />
            ))}
          </div>
        </div>
      </div>

      {/* Content */}
      <main className="flex-1 min-w-0">
        <div className="max-w-6xl w-full mx-auto px-4 sm:px-6 py-6">
          <Suspense fallback={<TabFallback />}>
            <Outlet />
          </Suspense>
        </div>
      </main>

      {prefsOpen && (
        <Suspense fallback={null}>
          <PreferencesModal onClose={() => setPrefsOpen(false)} />
        </Suspense>
      )}
    </div>
  );
}
