import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import {
  deleteConfluenceSpaceContent, deleteConnectorToken, fetchConfluencePages, fetchConfluenceStats,
  listConfluenceSpaces, listConnectorTokens, saveConfluenceToken, syncConfluenceSpace,
} from '../../services/connectorService';
import { ConfluenceSpaceSummary } from '../../types';
import {
  Cable, CheckCircle, Clock, FolderMinus, Play, RefreshCw, Search, Trash2, XCircle,
} from 'lucide-react';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card, CardBody, CardHeader } from '../ui/Card';
import Button from '../ui/Button';
import Input from '../ui/Input';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';
import { useConfirm } from '../ui/ConfirmDialog';

const STATUS_BADGE: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'neutral'> = {
  COMPLETED: 'success',
  SYNCING: 'primary',
  PENDING: 'warning',
  FAILED: 'danger',
  SKIPPED: 'neutral',
};

/** Connect a Confluence site and ingest its spaces into the same searchable corpus as uploaded
 * documents — the connector equivalent of confluencebot's space-based ingestion, built on top of
 * document-ingestor's existing multi-tenant ConfluenceConnectorService/ConnectorController. */
export default function ConnectorsTab() {
  const { token } = useAuth();
  const toast = useToast();
  const confirm = useConfirm();
  const queryClient = useQueryClient();

  const [siteUrl, setSiteUrl] = useState('');
  const [accessToken, setAccessToken] = useState('');
  const [product, setProduct] = useState('');
  const [version, setVersion] = useState('');
  const [spaces, setSpaces] = useState<ConfluenceSpaceSummary[] | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

  const tokensQuery = useQuery({
    queryKey: ['connectors', 'tokens'],
    queryFn: () => listConnectorTokens(token!),
    enabled: !!token,
  });
  const confluenceToken = tokensQuery.data?.find(t => t.provider === 'confluence') ?? null;

  const statsQuery = useQuery({
    queryKey: ['connectors', 'confluence', 'stats'],
    queryFn: () => fetchConfluenceStats(token!),
    enabled: !!token && !!confluenceToken,
    refetchInterval: 8000,
  });

  const pagesQuery = useQuery({
    queryKey: ['connectors', 'confluence', 'pages'],
    queryFn: () => fetchConfluencePages(token!),
    enabled: !!token && !!confluenceToken,
    refetchInterval: query => {
      const active = query.state.data?.some(p => p.syncStatus === 'PENDING' || p.syncStatus === 'SYNCING');
      return active ? 5000 : false;
    },
  });

  // Distinct spaces with at least one synced page, for the "manage ingested spaces" panel below —
  // derived from the same sync history rather than a dedicated endpoint.
  const ingestedSpaces = useMemo(() => {
    const counts = new Map<string, number>();
    for (const p of pagesQuery.data ?? []) {
      if (!p.spaceKey) continue;
      counts.set(p.spaceKey, (counts.get(p.spaceKey) ?? 0) + 1);
    }
    return Array.from(counts.entries())
      .map(([spaceKey, pageCount]) => ({ spaceKey, pageCount }))
      .sort((a, b) => a.spaceKey.localeCompare(b.spaceKey));
  }, [pagesQuery.data]);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['connectors'] });
  };

  const saveToken = useMutation({
    mutationFn: () => saveConfluenceToken(token!, siteUrl.trim(), accessToken.trim()),
    onSuccess: () => {
      setSiteUrl(''); setAccessToken('');
      refresh();
      toast.success('Confluence connected.');
    },
    onError: e => toast.error(e instanceof Error ? e.message : 'Could not save the Confluence token.'),
  });

  const removeToken = useMutation({
    mutationFn: (id: string) => deleteConnectorToken(token!, id),
    onSuccess: () => { setSpaces(null); refresh(); toast.success('Confluence disconnected.'); },
    onError: e => toast.error(e instanceof Error ? e.message : 'Could not disconnect Confluence.'),
  });

  const browseSpaces = useMutation({
    mutationFn: () => listConfluenceSpaces(token!),
    onSuccess: setSpaces,
    onError: e => toast.error(e instanceof Error ? e.message : 'Could not list Confluence spaces.'),
  });

  const [syncingKey, setSyncingKey] = useState<string | null>(null);
  const syncSpace = useMutation({
    mutationFn: (spaceKey: string) => syncConfluenceSpace(token!, spaceKey, product.trim(), version.trim()),
    onMutate: spaceKey => setSyncingKey(spaceKey),
    onSuccess: (_res, spaceKey) => {
      toast.success(`Sync started for space "${spaceKey}". Track progress in the sync history below.`);
      setHistoryOpen(true);
      refresh();
    },
    onError: e => toast.error(e instanceof Error ? e.message : 'Could not start the sync.'),
    onSettled: () => setSyncingKey(null),
  });

  const handleRemoveToken = async () => {
    if (!confluenceToken) return;
    const confirmed = await confirm({
      title: 'Disconnect Confluence',
      message: 'Remove this Confluence connection? Already-ingested pages stay searchable; syncing stops until you reconnect.',
      confirmLabel: 'Disconnect',
      danger: true,
    });
    if (confirmed) removeToken.mutate(confluenceToken.id);
  };

  const [purgingKey, setPurgingKey] = useState<string | null>(null);
  const purgeSpace = useMutation({
    mutationFn: (spaceKey: string) => deleteConfluenceSpaceContent(token!, spaceKey),
    onMutate: spaceKey => setPurgingKey(spaceKey),
    onSuccess: res => {
      toast.success(`Removed ${res.documentsDeleted} document(s) from space "${res.spaceKey}".`);
      refresh();
    },
    onError: e => toast.error(e instanceof Error ? e.message : 'Could not remove the space\'s content.'),
    onSettled: () => setPurgingKey(null),
  });

  const handlePurgeSpace = async (spaceKey: string) => {
    const confirmed = await confirm({
      title: 'Remove space content',
      message: `Permanently delete every document ingested from space "${spaceKey}"? This removes them from search immediately and cannot be undone.`,
      confirmLabel: 'Remove content',
      danger: true,
    });
    if (confirmed) purgeSpace.mutate(spaceKey);
  };

  return (
    <div>
      <PageHeader
        title="Connectors"
        description="Ingest content straight from Confluence — spaces sync into the same searchable corpus as uploaded documents."
      />

      <div className="space-y-6">
        <Card>
          <CardBody>
            <h2 className="text-base font-semibold text-foreground mb-4 flex items-center gap-2">
              <Cable className="w-4 h-4 text-primary" />
              Confluence
            </h2>

            {tokensQuery.isLoading ? (
              <SkeletonRow columns={2} />
            ) : confluenceToken ? (
              <div className="flex items-center justify-between gap-4 rounded-lg border border-border p-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                    <CheckCircle className="w-4 h-4 text-success flex-shrink-0" />
                    Connected
                  </div>
                  <div className="text-xs text-muted-foreground mt-1 truncate">{confluenceToken.siteUrl}</div>
                </div>
                <Button variant="ghost" size="sm" onClick={handleRemoveToken} loading={removeToken.isPending} leftIcon={<Trash2 className="w-3.5 h-3.5" />}>
                  Disconnect
                </Button>
              </div>
            ) : (
              <form onSubmit={e => { e.preventDefault(); saveToken.mutate(); }} className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Input
                    label="Confluence site URL *"
                    value={siteUrl}
                    onChange={e => setSiteUrl(e.target.value)}
                    required
                    placeholder="https://your-domain.atlassian.net"
                    hint="Cloud sites only (*.atlassian.net) by default."
                  />
                  <Input
                    label="Access token *"
                    type="password"
                    value={accessToken}
                    onChange={e => setAccessToken(e.target.value)}
                    required
                    placeholder="Confluence API/OAuth bearer token"
                  />
                </div>
                <Button type="submit" loading={saveToken.isPending} disabled={!siteUrl.trim() || !accessToken.trim()}>
                  Connect Confluence
                </Button>
              </form>
            )}

            {confluenceToken && (
              <div className="mt-5 pt-5 border-t border-border space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Input
                    label="Product *"
                    value={product}
                    onChange={e => setProduct(e.target.value)}
                    placeholder="e.g. case360"
                    hint="Tag every page synced from a space with this product/version."
                  />
                  <Input label="Version *" value={version} onChange={e => setVersion(e.target.value)} placeholder="e.g. 23.4" />
                </div>

                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => browseSpaces.mutate()}
                  loading={browseSpaces.isPending}
                  leftIcon={<Search className="w-3.5 h-3.5" />}
                >
                  Browse spaces
                </Button>

                {spaces && (
                  spaces.length === 0 ? (
                    <EmptyState icon={Search} title="No spaces found" description="This Confluence account has no visible spaces." />
                  ) : (
                    <div className="overflow-x-auto rounded-lg border border-border">
                      <table className="w-full text-sm">
                        <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                          <tr>
                            <th className="px-4 py-2 text-left">Key</th>
                            <th className="px-4 py-2 text-left">Name</th>
                            <th className="px-4 py-2 text-right">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {spaces.map(space => (
                            <tr key={space.key}>
                              <td className="px-4 py-2 font-mono text-xs">{space.key}</td>
                              <td className="px-4 py-2">{space.name}</td>
                              <td className="px-4 py-2 text-right">
                                <Button
                                  size="sm"
                                  onClick={() => syncSpace.mutate(space.key)}
                                  loading={syncingKey === space.key}
                                  disabled={!product.trim() || !version.trim()}
                                  leftIcon={<Play className="w-3 h-3" />}
                                >
                                  Sync
                                </Button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )
                )}
              </div>
            )}
          </CardBody>
        </Card>

        {confluenceToken && (
          <motion.div variants={staggerContainer} initial="hidden" animate="visible">
            <motion.div variants={fadeInUp} className="grid grid-cols-3 gap-4 mb-4">
              {[
                { label: 'Completed', value: statsQuery.data?.completed ?? 0, color: 'text-success' },
                { label: 'Pending', value: statsQuery.data?.pending ?? 0, color: 'text-warning' },
                { label: 'Failed', value: statsQuery.data?.failed ?? 0, color: 'text-danger' },
              ].map(({ label, value, color }) => (
                <Card key={label} className="p-4 text-center">
                  <div className={`text-2xl font-bold ${color}`}>{value}</div>
                  <div className="text-xs text-muted-foreground mt-1">{label}</div>
                </Card>
              ))}
            </motion.div>

            {ingestedSpaces.length > 0 && (
              <Card className="mb-4">
                <CardHeader>
                  <h2 className="text-sm font-semibold text-foreground">Manage ingested spaces</h2>
                </CardHeader>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                      <tr>
                        <th className="px-4 py-2 text-left">Space</th>
                        <th className="px-4 py-2 text-right">Synced pages</th>
                        <th className="px-4 py-2 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {ingestedSpaces.map(({ spaceKey, pageCount }) => (
                        <tr key={spaceKey}>
                          <td className="px-4 py-2 font-mono text-xs">{spaceKey}</td>
                          <td className="px-4 py-2 text-right text-muted-foreground">{pageCount}</td>
                          <td className="px-4 py-2 text-right">
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={purgingKey === spaceKey}
                              leftIcon={<FolderMinus className="w-3.5 h-3.5" />}
                              onClick={() => handlePurgeSpace(spaceKey)}
                            >
                              Remove content
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            )}

            <Card>
              <CardHeader className="flex items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={() => setHistoryOpen(open => !open)}
                  className="text-sm font-semibold text-foreground"
                >
                  Sync history
                </button>
                {historyOpen && (
                  <Button size="sm" variant="ghost" onClick={() => queryClient.invalidateQueries({ queryKey: ['connectors', 'confluence', 'pages'] })} leftIcon={<RefreshCw className="w-3.5 h-3.5" />}>
                    Refresh
                  </Button>
                )}
              </CardHeader>
              {historyOpen && (
                pagesQuery.isLoading ? (
                  <div className="p-4"><SkeletonRow columns={4} /></div>
                ) : !pagesQuery.data || pagesQuery.data.length === 0 ? (
                  <EmptyState icon={Cable} title="No pages synced yet" description="Browse a space above and sync it to see progress here." />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                        <tr>
                          <th className="px-4 py-2 text-left">Page</th>
                          <th className="px-4 py-2 text-left">Space</th>
                          <th className="px-4 py-2 text-left">Status</th>
                          <th className="px-4 py-2 text-left">Last synced</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {pagesQuery.data.map(p => (
                          <tr key={p.id}>
                            <td className="px-4 py-2">
                              <div className="text-foreground">{p.title ?? p.externalId}</div>
                              {p.errorMessage && <div className="text-xs text-danger mt-0.5 max-w-xs truncate" title={p.errorMessage}>{p.errorMessage}</div>}
                            </td>
                            <td className="px-4 py-2 font-mono text-xs">{p.spaceKey ?? '—'}</td>
                            <td className="px-4 py-2">
                              <Badge variant={STATUS_BADGE[p.syncStatus] ?? 'neutral'}>
                                {p.syncStatus === 'COMPLETED' && <CheckCircle className="w-3.5 h-3.5" />}
                                {(p.syncStatus === 'PENDING' || p.syncStatus === 'SYNCING') && <Clock className="w-3.5 h-3.5" />}
                                {p.syncStatus === 'FAILED' && <XCircle className="w-3.5 h-3.5" />}
                                {p.syncStatus}
                              </Badge>
                            </td>
                            <td className="px-4 py-2 text-muted-foreground text-xs">
                              {p.lastSyncedAt ? new Date(p.lastSyncedAt).toLocaleString() : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )
              )}
            </Card>
          </motion.div>
        )}
      </div>
    </div>
  );
}
