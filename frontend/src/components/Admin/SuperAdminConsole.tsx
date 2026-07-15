import { Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Building2 } from 'lucide-react';
import AdminLayout, { AdminNavItem } from './AdminLayout';
import TenantsPage from './TenantsPage';

const NAV_ITEMS: AdminNavItem[] = [
  { to: '/admin/tenants', label: 'Tenants', icon: Building2 },
];

export default function SuperAdminConsole() {
  return (
    <Routes>
      <Route element={<AdminLayout navItems={NAV_ITEMS} title="Super Admin" />}>
        <Route index element={<Navigate to="/admin/tenants" replace />} />
        <Route path="tenants" element={<Suspense fallback={null}><TenantsPage /></Suspense>} />
        <Route path="*" element={<Navigate to="/admin/tenants" replace />} />
      </Route>
    </Routes>
  );
}
