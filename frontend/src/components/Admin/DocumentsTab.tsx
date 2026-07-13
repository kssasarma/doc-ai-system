import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { DocumentInfo, IngestionStatus, TenantUser, Group } from '../../types';
import { fetchDocuments, fetchIngestionStatus, uploadDocument, retriggerDocument, reprocessFailedDocuments, getDocumentDownloadUrl } from '../../services/adminService';
import { getTenantUsers } from '../../services/tenantService';
import { listGroups } from '../../services/groupService';
import DocumentAccessManager from './DocumentAccessManager';
import { Upload, RefreshCw, RotateCcw, Download, AlertCircle, CheckCircle, Clock, XCircle, Lock, X, FileText } from 'lucide-react';
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

export default function DocumentsTab() {
  const { token, user } = useAuth();
  const toast = useToast();
  const [documents, setDocuments] = useState<DocumentInfo[]>([]);
  const [status, setStatus] = useState<IngestionStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [product, setProduct] = useState('');
  const [version, setVersion] = useState('');
  const [docName, setDocName] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [isBulkReprocessing, setIsBulkReprocessing] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [tenantUsers, setTenantUsers] = useState<TenantUser[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [justUploaded, setJustUploaded] = useState<DocumentInfo | null>(null);
  const [managingDoc, setManagingDoc] = useState<DocumentInfo | null>(null);

  const loadData = useCallback(async () => {
    if (!token) return;
    setError('');
    try {
      const [docs, stat] = await Promise.all([fetchDocuments(token), fetchIngestionStatus(token)]);
      setDocuments(docs);
      setStatus(stat);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load data');
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 10000);
    return () => clearInterval(interval);
  }, [loadData]);

  useEffect(() => {
    if (!token || !user?.tenantId) return;
    getTenantUsers(token, user.tenantId).then(setTenantUsers).catch(() => {});
  }, [token, user?.tenantId]);

  useEffect(() => {
    if (!token) return;
    listGroups(token).then(setGroups).catch(() => {});
  }, [token]);

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
                <DocumentAccessManager token={token!} documentId={justUploaded.id} tenantUsers={tenantUsers} groups={groups} />
              </motion.div>
            )}
          </CardBody>
        </Card>

        {/* Document list */}
        <Card>
          <CardHeader className="flex items-center justify-between gap-2">
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
          {isLoading ? (
            <div className="divide-y divide-border">{[0, 1, 2].map(i => <SkeletonRow key={i} columns={5} />)}</div>
          ) : error ? (
            <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
          ) : documents.length === 0 ? (
            <EmptyState icon={FileText} title="No documents yet" description="Upload your first document above to get started." />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-6 py-3 text-left">Document</th>
                    <th className="px-6 py-3 text-left">Product / Version</th>
                    <th className="px-6 py-3 text-left">Status</th>
                    <th className="px-6 py-3 text-right">Chunks</th>
                    <th className="px-6 py-3 text-left">Created</th>
                    <th className="px-6 py-3 text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {documents.map(doc => (
                    <tr key={doc.id} className="hover:bg-surface-hover transition-colors">
                      <td className="px-6 py-4">
                        <div className="font-medium text-foreground">{doc.documentName}</div>
                        {doc.errorMessage && <div className="text-xs text-danger mt-0.5 max-w-xs truncate" title={doc.errorMessage}>{doc.errorMessage}</div>}
                      </td>
                      <td className="px-6 py-4 text-muted-foreground">{doc.product} <span className="text-muted-foreground/60">v</span>{doc.version}</td>
                      <td className="px-6 py-4">
                        <Badge variant={STATUS_BADGE[doc.status] || 'neutral'}>
                          {STATUS_ICONS[doc.status]}{doc.status}
                        </Badge>
                      </td>
                      <td className="px-6 py-4 text-right text-muted-foreground">{doc.chunkCount ?? '—'}</td>
                      <td className="px-6 py-4 text-muted-foreground text-xs">{doc.createdAt ? new Date(doc.createdAt).toLocaleString() : '—'}</td>
                      <td className="px-6 py-4">
                        <div className="flex items-center justify-center gap-2">
                          <Button variant="secondary" size="sm" onClick={() => setManagingDoc(doc)} leftIcon={<Lock className="w-3.5 h-3.5" />}>Access</Button>
                          {(doc.status === 'FAILED' || doc.status === 'PENDING') && (
                            <Button variant="outline" size="sm" onClick={() => handleRetrigger(doc)} leftIcon={<RefreshCw className="w-3.5 h-3.5" />}>Retry</Button>
                          )}
                          {doc.status !== 'COMPLETED' && (
                            <IconButton
                              label="Download original file"
                              variant="ghost"
                              size="sm"
                              onClick={() => handleDownload(doc)}
                              disabled={downloadingId === doc.id}
                            >
                              <Download className="w-3.5 h-3.5" />
                            </IconButton>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      <Modal open={!!managingDoc} onClose={() => setManagingDoc(null)} title={managingDoc ? `Access — ${managingDoc.documentName}` : ''} icon={<Lock size={15} className="text-primary" />}>
        {managingDoc && (
          <ModalBody>
            <DocumentAccessManager token={token!} documentId={managingDoc.id} tenantUsers={tenantUsers} groups={groups} />
          </ModalBody>
        )}
      </Modal>
    </div>
  );
}
