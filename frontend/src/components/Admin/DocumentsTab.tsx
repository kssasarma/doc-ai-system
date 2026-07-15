import React, { useEffect, useMemo, useState, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { DocumentInfo } from '../../types';
import {
  fetchDocuments, fetchIngestionStatus, uploadDocument, retriggerDocument,
  reprocessFailedDocuments, getDocumentDownloadUrl, deleteDocument,
} from '../../services/adminService';
import DocumentAccessManager from './DocumentAccessManager';
import {
  Upload, RefreshCw, RotateCcw, Download, AlertCircle, CheckCircle, Clock, XCircle,
  Lock, X, FileText, Search, ChevronLeft, ChevronRight, ChevronDown, Trash2, Star,
} from 'lucide-react';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import { cn } from '../../lib/cn';
import PageHeader from '../ui/PageHeader';
import { Card, CardHeader, CardBody } from '../ui/Card';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Input from '../ui/Input';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import Modal, { ModalBody } from '../ui/Modal';
import { useToast } from '../ui/Toast';
import { useConfirm } from '../ui/ConfirmDialog';

const STATUS_ICONS: Record<string, React.ReactNode> = {
  COMPLETED: <CheckCircle className="w-4 h-4" />,
  PROCESSING: <Clock className="w-4 h-4 animate-spin" />,
  PENDING: <Clock className="w-4 h-4" />,
  FAILED: <XCircle className="w-4 h-4" />,
};

const STATUS_BADGE: Record<string, 'success' | 'primary' | 'warning' | 'danger'> = {
  COMPLETED: 'success',
  PROCESSING: 'primary',
  PENDING: 'warning',
  FAILED: 'danger',
};

const PAGE_SIZE = 50;

export default function DocumentsTab() {
  const { token } = useAuth();
  const toast = useToast();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [product, setProduct] = useState('');
  const [version, setVersion] = useState('');
  const [docName, setDocName] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [isBulkReprocessing, setIsBulkReprocessing] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [justUploaded, setJustUploaded] = useState<DocumentInfo | null>(null);
  const [managingDoc, setManagingDoc] = useState<DocumentInfo | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  const [collapsedProducts, setCollapsedProducts] = useState<Set<string>>(new Set());

  useEffect(() => {
    const handle = setTimeout(() => { setDebouncedQuery(searchQuery.trim()); setPage(0); }, 300);
    return () => clearTimeout(handle);
  }, [searchQuery]);

  const documentsQuery = useQuery({
    queryKey: ['documents', debouncedQuery, page],
    queryFn: () => fetchDocuments(token!, { q: debouncedQuery || undefined, page, size: PAGE_SIZE }),
    enabled: !!token,
    // Only keep polling while something is actually in flight — an all-COMPLETED/FAILED corpus
    // has nothing left to watch, and the previous unconditional 10s interval kept hitting the
    // API forever even on an admin's idle tab. Also stops polling when the tab isn't visible.
    refetchInterval: (query) => {
      if (document.visibilityState !== 'visible') return false;
      const docs = query.state.data?.content;
      const stillWorking = docs?.some(d => d.status === 'PROCESSING' || d.status === 'PENDING');
      return stillWorking ? 10000 : false;
    },
  });
  const statusQuery = useQuery({
    queryKey: ['ingestion-status'],
    queryFn: () => fetchIngestionStatus(token!),
    enabled: !!token,
    refetchInterval: () => (documentsQuery.data?.content.some(d => d.status === 'PROCESSING' || d.status === 'PENDING') ? 10000 : false),
  });

  const documents = documentsQuery.data?.content ?? [];
  const totalPages = documentsQuery.data?.totalPages ?? 0;
  const totalElements = documentsQuery.data?.totalElements ?? 0;
  const status = statusQuery.data ?? null;
  const isLoading = documentsQuery.isLoading;
  const error = documentsQuery.isError ? 'Failed to load data' : '';

  // Grouped by product for display — within the current page only (pagination and "group by
  // product" are in some tension at a page boundary, but that's an acceptable trade at the scale
  // this backs). "Latest" is the highest version per product group by natural/numeric string
  // comparison, best-effort within what's currently loaded.
  const groups = useMemo(() => {
    const byProduct = new Map<string, DocumentInfo[]>();
    documents.forEach(doc => {
      const list = byProduct.get(doc.product) ?? [];
      list.push(doc);
      byProduct.set(doc.product, list);
    });
    return Array.from(byProduct.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([productName, docs]) => {
        const sorted = [...docs].sort((a, b) => b.version.localeCompare(a.version, undefined, { numeric: true }));
        return { product: productName, docs: sorted, latestVersion: sorted[0]?.version };
      });
  }, [documents]);

  const loadData = () => {
    queryClient.invalidateQueries({ queryKey: ['documents'] });
    queryClient.invalidateQueries({ queryKey: ['ingestion-status'] });
  };

  const toggleCollapsed = (productName: string) => {
    setCollapsedProducts(prev => {
      const next = new Set(prev);
      if (next.has(productName)) next.delete(productName); else next.add(productName);
      return next;
    });
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadFile || !token) return;
    setIsUploading(true);
    try {
      const created = await uploadDocument(token, uploadFile, product, version, docName || undefined);
      setJustUploaded(created);
      setUploadFile(null);
      setProduct(''); setVersion(''); setDocName('');
      if (fileInputRef.current) fileInputRef.current.value = '';
      loadData();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Upload failed.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleRetrigger = async (doc: DocumentInfo) => {
    if (!token) return;
    try {
      await retriggerDocument(token, doc.id);
      loadData();
      toast.success(`Retriggered processing for "${doc.documentName}"`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Retrigger failed');
    }
  };

  const handleReprocessFailed = async () => {
    if (!token) return;
    setIsBulkReprocessing(true);
    try {
      const result = await reprocessFailedDocuments(token);
      loadData();
      if (result.started > 0) {
        toast.success(`Restarted processing for ${result.started} of ${result.totalFailed} failed document${result.totalFailed !== 1 ? 's' : ''}.`);
      }
      if (result.skipped.length > 0) {
        toast.error(`${result.skipped.length} document${result.skipped.length !== 1 ? 's' : ''} could not be restarted — see server logs.`);
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Bulk reprocess failed.');
    } finally {
      setIsBulkReprocessing(false);
    }
  };

  const handleDownload = async (doc: DocumentInfo) => {
    if (!token) return;
    setDownloadingId(doc.id);
    try {
      const url = await getDocumentDownloadUrl(token, doc.id);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Could not get download link.');
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDelete = async (doc: DocumentInfo) => {
    if (!token) return;
    const confirmed = await confirm({
      title: 'Delete document',
      message: `Delete "${doc.documentName}"? This removes it and all its chunks permanently.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) return;
    setDeletingId(doc.id);
    try {
      await deleteDocument(token, doc.id);
      loadData();
      toast.success(`Deleted "${doc.documentName}"`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed.');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div>
      <PageHeader title="Documents" description="Upload documentation and manage per-document access." />

      <div className="space-y-6">
        {/* Status summary */}
        {status && (
          <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="grid grid-cols-2 sm:grid-cols-5 gap-4">
            {[
              { label: 'Total', value: status.totalDocuments, color: 'text-foreground' },
              { label: 'Completed', value: status.completed, color: 'text-success' },
              { label: 'Processing', value: status.processing, color: 'text-primary' },
              { label: 'Failed', value: status.failed, color: 'text-danger' },
              { label: 'Total Chunks', value: status.totalChunks, color: 'text-accent' },
            ].map(({ label, value, color }) => (
              <motion.div key={label} variants={fadeInUp}>
                <Card className="p-4 text-center">
                  <div className={cn('text-2xl font-bold', color)}>{value}</div>
                  <div className="text-xs text-muted-foreground mt-1">{label}</div>
                </Card>
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* Upload form */}
        <Card>
          <CardBody>
            <h2 className="text-base font-semibold text-foreground mb-4 flex items-center gap-2">
              <Upload className="w-4 h-4 text-primary" />
              Upload Document
            </h2>
            <form onSubmit={handleUpload} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input label="Product *" type="text" value={product} onChange={e => setProduct(e.target.value)} required placeholder="e.g. case360" />
                <Input label="Version *" type="text" value={version} onChange={e => setVersion(e.target.value)} required placeholder="e.g. 23.4" />
                <Input label="Document Name (optional)" type="text" value={docName} onChange={e => setDocName(e.target.value)} placeholder="Defaults to filename" />
                <div>
                  <label htmlFor="upload-file" className="block text-sm font-medium text-foreground mb-1">File * (.pdf, .chm, .html, .htm, .txt, or .md)</label>
                  <input
                    id="upload-file"
                    ref={fileInputRef} type="file" accept=".pdf,.chm,.html,.htm,.txt,.md" onChange={e => setUploadFile(e.target.files?.[0] || null)} required
                    className="w-full px-3 py-2 border border-border bg-surface rounded-lg text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary file:mr-3 file:py-1 file:px-2 file:rounded file:border-0 file:text-xs file:bg-primary/10 file:text-primary"
                  />
                </div>
              </div>
              <Button type="submit" loading={isUploading} disabled={!uploadFile} leftIcon={!isUploading ? <Upload className="w-4 h-4" /> : undefined}>
                {isUploading ? 'Uploading…' : 'Upload & Process'}
              </Button>
            </form>

            {justUploaded && (
              <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} className="mt-5 pt-5 border-t border-border overflow-hidden">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2 text-sm text-success">
                    <CheckCircle className="w-4 h-4 flex-shrink-0" />
                    "{justUploaded.documentName}" uploaded and processing started. Grant access now, or later from the table below.
                  </div>
                  <IconButton label="Dismiss" variant="ghost" size="sm" onClick={() => setJustUploaded(null)}><X size={15} /></IconButton>
                </div>
                <DocumentAccessManager token={token!} documentId={justUploaded.id} />
              </motion.div>
            )}
          </CardBody>
        </Card>

        {/* Document list */}
        <Card>
          <CardHeader className="flex items-center justify-between gap-2 flex-wrap">
            <h2 className="text-base font-semibold text-foreground">Documents</h2>
            <div className="flex items-center gap-2">
              {!!status?.failed && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleReprocessFailed}
                  loading={isBulkReprocessing}
                  leftIcon={!isBulkReprocessing ? <RotateCcw className="w-3.5 h-3.5" /> : undefined}
                >
                  {isBulkReprocessing ? 'Reprocessing…' : `Reprocess ${status.failed} failed`}
                </Button>
              )}
              <Button variant="ghost" size="sm" onClick={loadData} leftIcon={<RefreshCw className="w-3.5 h-3.5" />}>Refresh</Button>
            </div>
          </CardHeader>
          <div className="px-4 pt-4">
            <div className="relative">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <Input
                type="text"
                aria-label="Search documents"
                placeholder="Search by document name or product…"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>
          </div>
          {isLoading ? (
            <div className="divide-y divide-border mt-4">{[0, 1, 2].map(i => <SkeletonRow key={i} columns={5} />)}</div>
          ) : error ? (
            <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
          ) : documents.length === 0 ? (
            <EmptyState icon={FileText} title="No documents found" description={debouncedQuery ? 'Try a different search term.' : 'Upload your first document above to get started.'} />
          ) : (
            <div className="overflow-x-auto mt-2">
              <table className="w-full text-sm">
                <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-6 py-3 text-left">Document</th>
                    <th className="px-6 py-3 text-left">Version</th>
                    <th className="px-6 py-3 text-left">Status</th>
                    <th className="px-6 py-3 text-right">Chunks</th>
                    <th className="px-6 py-3 text-left">Created</th>
                    <th className="px-6 py-3 text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {groups.map(group => {
                    const collapsed = collapsedProducts.has(group.product);
                    return (
                      <React.Fragment key={group.product}>
                        <tr className="bg-muted/50">
                          <td colSpan={6} className="px-6 py-2">
                            <button
                              onClick={() => toggleCollapsed(group.product)}
                              className="flex items-center gap-1.5 text-xs font-semibold text-foreground uppercase tracking-wide"
                            >
                              {collapsed ? <ChevronRight size={13} /> : <ChevronDown size={13} />}
                              {group.product}
                              <span className="font-normal normal-case text-muted-foreground">({group.docs.length})</span>
                            </button>
                          </td>
                        </tr>
                        {!collapsed && group.docs.map(doc => (
                          <tr key={doc.id} className="hover:bg-surface-hover transition-colors">
                            <td className="px-6 py-4">
                              <div className="font-medium text-foreground">{doc.documentName}</div>
                              {doc.errorMessage && <div className="text-xs text-danger mt-0.5 max-w-xs truncate" title={doc.errorMessage}>{doc.errorMessage}</div>}
                            </td>
                            <td className="px-6 py-4">
                              <div className="flex items-center gap-1.5">
                                <Badge variant="neutral">{doc.version}</Badge>
                                {doc.version === group.latestVersion && (
                                  <span title="Latest version for this product" className="text-warning"><Star size={12} fill="currentColor" /></span>
                                )}
                              </div>
                            </td>
                            <td className="px-6 py-4">
                              <Badge variant={STATUS_BADGE[doc.status] || 'neutral'}>
                                {STATUS_ICONS[doc.status]}{doc.status}
                              </Badge>
                            </td>
                            <td className="px-6 py-4 text-right text-muted-foreground">{doc.chunkCount ?? '—'}</td>
                            <td className="px-6 py-4 text-muted-foreground text-xs">{doc.createdAt ? new Date(doc.createdAt).toLocaleString() : '—'}</td>
                            <td className="px-6 py-4">
                              <div className="flex items-center justify-center gap-1.5">
                                <Button variant="secondary" size="sm" onClick={() => setManagingDoc(doc)} leftIcon={<Lock className="w-3.5 h-3.5" />}>Access</Button>
                                {(doc.status === 'FAILED' || doc.status === 'PENDING') && (
                                  <IconButton label="Retry" variant="ghost" size="sm" onClick={() => handleRetrigger(doc)}>
                                    <RefreshCw className="w-3.5 h-3.5" />
                                  </IconButton>
                                )}
                                <IconButton
                                  label="Download original file"
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleDownload(doc)}
                                  disabled={downloadingId === doc.id}
                                >
                                  <Download className="w-3.5 h-3.5" />
                                </IconButton>
                                <IconButton
                                  label="Delete document"
                                  variant="danger"
                                  size="sm"
                                  onClick={() => handleDelete(doc)}
                                  disabled={deletingId === doc.id}
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </IconButton>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="px-4 py-3 border-t border-border flex items-center justify-between">
              <span className="text-xs text-muted-foreground">
                Page {page + 1} of {totalPages} ({totalElements} total)
              </span>
              <div className="flex items-center gap-2">
                <IconButton label="Previous page" variant="ghost" size="sm" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
                  <ChevronLeft size={16} />
                </IconButton>
                <IconButton label="Next page" variant="ghost" size="sm" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>
                  <ChevronRight size={16} />
                </IconButton>
              </div>
            </div>
          )}
        </Card>
      </div>

      <Modal open={!!managingDoc} onClose={() => setManagingDoc(null)} title={managingDoc ? `Access — ${managingDoc.documentName}` : ''} icon={<Lock size={15} className="text-primary" />}>
        {managingDoc && (
          <ModalBody>
            <DocumentAccessManager token={token!} documentId={managingDoc.id} />
          </ModalBody>
        )}
      </Modal>
    </div>
  );
}
