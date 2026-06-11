import React, { Suspense, lazy } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, BarChart2, FileText, Map, TrendingUp, DollarSign, Users, Shield, HelpCircle } from 'lucide-react';

const OverviewTab = lazy(() => import('./OverviewTab'));
const DocumentsTab = lazy(() => import('./DocumentsTab'));
const CoverageTab = lazy(() => import('./CoverageTab'));
const QueryIntelligenceTab = lazy(() => import('./QueryIntelligenceTab'));
const CostTrackingTab = lazy(() => import('./CostTrackingTab'));
const UserAccessTab = lazy(() => import('./UserAccessTab'));
const AuditLogTab = lazy(() => import('./AuditLogTab'));
const EscalationsTab = lazy(() => import('./EscalationsTab'));

const TABS = [
  { id: 'overview', label: 'Overview', icon: <BarChart2 size={15} /> },
  { id: 'documents', label: 'Documents', icon: <FileText size={15} /> },
  { id: 'coverage', label: 'Coverage', icon: <Map size={15} /> },
  { id: 'query-intel', label: 'Query Intel', icon: <TrendingUp size={15} /> },
  { id: 'cost', label: 'Cost', icon: <DollarSign size={15} /> },
  { id: 'users', label: 'Users & Access', icon: <Users size={15} /> },
  { id: 'audit', label: 'Audit Log', icon: <Shield size={15} /> },
  { id: 'escalations', label: 'Escalations', icon: <HelpCircle size={15} /> },
] as const;

type TabId = (typeof TABS)[number]['id'];

function TabFallback() {
  return (
    <div className="p-12 text-center text-gray-400">
      <div className="animate-spin w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full mx-auto mb-2" />
      Loading…
    </div>
  );
}

export default function AdminPanel() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = React.useState<TabId>('overview');

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center gap-4 flex-shrink-0">
        <button
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 text-sm transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Chat
        </button>
        <h1 className="text-xl font-semibold text-gray-900">Admin Panel</h1>
      </div>

      {/* Tab bar */}
      <div className="bg-white border-b border-gray-200 px-6 flex-shrink-0 overflow-x-auto">
        <div className="flex gap-0 min-w-max">
          {TABS.map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === tab.id
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-800 hover:border-gray-300'
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Tab content */}
      <div className="flex-1 max-w-7xl w-full mx-auto px-6 py-6">
        <Suspense fallback={<TabFallback />}>
          {activeTab === 'overview' && <OverviewTab />}
          {activeTab === 'documents' && <DocumentsTab />}
          {activeTab === 'coverage' && <CoverageTab />}
          {activeTab === 'query-intel' && <QueryIntelligenceTab />}
          {activeTab === 'cost' && <CostTrackingTab />}
          {activeTab === 'users' && <UserAccessTab />}
          {activeTab === 'audit' && <AuditLogTab />}
          {activeTab === 'escalations' && <EscalationsTab />}
        </Suspense>
      </div>
    </div>
  );
}
