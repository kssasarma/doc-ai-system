import { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import {
  BarChart2, FileText, Map, TrendingUp, DollarSign, Users, UsersRound, Shield,
  HelpCircle, MessageSquare, AlertTriangle, Lock, Settings as SettingsIcon, ShieldAlert,
} from 'lucide-react';
import AdminLayout, { AdminNavItem } from './AdminLayout';

const OverviewTab = lazy(() => import('./OverviewTab'));
const DocumentsTab = lazy(() => import('./DocumentsTab'));
const CoverageTab = lazy(() => import('./CoverageTab'));
const QueryIntelligenceTab = lazy(() => import('./QueryIntelligenceTab'));
const CostTrackingTab = lazy(() => import('./CostTrackingTab'));
const UsersPage = lazy(() => import('./UsersPage'));
const GroupsPage = lazy(() => import('./GroupsPage'));
const AuditLogTab = lazy(() => import('./AuditLogTab'));
const EscalationsTab = lazy(() => import('./EscalationsTab'));
const FaqManagementTab = lazy(() => import('./FaqManagementTab'));
const GapReportTab = lazy(() => import('./GapReportTab'));
const GdprTab = lazy(() => import('./GdprTab'));
const PiiFlagsTab = lazy(() => import('./PiiFlagsTab'));
const SettingsPage = lazy(() => import('./SettingsPage'));

const NAV_ITEMS: AdminNavItem[] = [
  { to: '/admin/overview', label: 'Overview', icon: BarChart2 },
  { to: '/admin/documents', label: 'Documents', icon: FileText },
  { to: '/admin/coverage', label: 'Coverage', icon: Map },
  { to: '/admin/query-intelligence', label: 'Query Intel', icon: TrendingUp },
  { to: '/admin/faq', label: 'FAQ Review', icon: MessageSquare },
  { to: '/admin/gap-reports', label: 'Gap Reports', icon: AlertTriangle },
  { to: '/admin/pii-flags', label: 'PII Review', icon: ShieldAlert },
  { to: '/admin/cost', label: 'Cost', icon: DollarSign },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/groups', label: 'Groups', icon: UsersRound },
  { to: '/admin/audit-log', label: 'Audit Log', icon: Shield },
  { to: '/admin/escalations', label: 'Escalations', icon: HelpCircle },
  { to: '/admin/gdpr', label: 'GDPR', icon: Lock },
  { to: '/admin/settings', label: 'Settings', icon: SettingsIcon },
];

export default function TenantAdminConsole() {
  return (
    <Routes>
      <Route element={<AdminLayout navItems={NAV_ITEMS} title="Admin Console" />}>
        <Route index element={<Navigate to="/admin/overview" replace />} />
        <Route path="overview" element={<Suspense fallback={null}><OverviewTab /></Suspense>} />
        <Route path="documents" element={<Suspense fallback={null}><DocumentsTab /></Suspense>} />
        <Route path="coverage" element={<Suspense fallback={null}><CoverageTab /></Suspense>} />
        <Route path="query-intelligence" element={<Suspense fallback={null}><QueryIntelligenceTab /></Suspense>} />
        <Route path="faq" element={<Suspense fallback={null}><FaqManagementTab /></Suspense>} />
        <Route path="gap-reports" element={<Suspense fallback={null}><GapReportTab /></Suspense>} />
        <Route path="pii-flags" element={<Suspense fallback={null}><PiiFlagsTab /></Suspense>} />
        <Route path="cost" element={<Suspense fallback={null}><CostTrackingTab /></Suspense>} />
        <Route path="users" element={<Suspense fallback={null}><UsersPage /></Suspense>} />
        <Route path="groups" element={<Suspense fallback={null}><GroupsPage /></Suspense>} />
        <Route path="audit-log" element={<Suspense fallback={null}><AuditLogTab /></Suspense>} />
        <Route path="escalations" element={<Suspense fallback={null}><EscalationsTab /></Suspense>} />
        <Route path="gdpr" element={<Suspense fallback={null}><GdprTab /></Suspense>} />
        <Route path="settings" element={<Suspense fallback={null}><SettingsPage /></Suspense>} />
        <Route path="*" element={<Navigate to="/admin/overview" replace />} />
      </Route>
    </Routes>
  );
}
