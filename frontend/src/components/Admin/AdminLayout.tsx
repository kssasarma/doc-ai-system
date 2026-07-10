import React, { Suspense } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { ArrowLeft, LucideIcon } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export interface AdminNavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

function TabFallback() {
  return (
    <div className="p-12 text-center text-gray-400">
      <div className="animate-spin w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full mx-auto mb-2" />
      Loading…
    </div>
  );
}

export default function AdminLayout({ navItems, title }: { navItems: AdminNavItem[]; title: string }) {
  const navigate = useNavigate();
  const { user, isSuperAdmin } = useAuth();

  const linkClasses = (isActive: boolean) =>
    `flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
      isActive ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
    }`;

  const tabClasses = (isActive: boolean) =>
    `flex items-center gap-1.5 px-4 py-3 text-sm font-medium border-b-2 whitespace-nowrap transition-colors ${
      isActive ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800 hover:border-gray-300'
    }`;

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col md:flex-row">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:flex-col md:w-60 md:flex-shrink-0 bg-white border-r border-gray-200">
        <div className="px-4 py-4 border-b border-gray-200">
          <button
            onClick={() => navigate('/')}
            className="flex items-center gap-2 text-gray-600 hover:text-gray-900 text-sm transition-colors mb-3"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Chat
          </button>
          <h1 className="text-lg font-semibold text-gray-900">{title}</h1>
          {user && (
            <p className="text-xs text-gray-400 mt-1 truncate">
              {user.username} · {isSuperAdmin ? 'Super Admin' : 'Tenant Admin'}
            </p>
          )}
        </div>
        <nav className="flex-1 overflow-y-auto p-3 space-y-1">
          {navItems.map(item => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => linkClasses(isActive)} end>
              <item.icon size={16} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      {/* Mobile top bar */}
      <div className="md:hidden bg-white border-b border-gray-200 flex-shrink-0">
        <div className="px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate('/')} className="flex items-center gap-1.5 text-gray-600 text-sm">
            <ArrowLeft className="w-4 h-4" /> Chat
          </button>
          <h1 className="text-base font-semibold text-gray-900">{title}</h1>
        </div>
        <div className="overflow-x-auto px-2">
          <div className="flex gap-0 min-w-max">
            {navItems.map(item => (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => tabClasses(isActive)} end>
                <item.icon size={14} />
                {item.label}
              </NavLink>
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
    </div>
  );
}
