import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft, Plus, NotebookText, BookOpen, Trash2, Upload, FileText,
  X, Check, CheckCircle, Clock, XCircle, AlertTriangle,
} from 'lucide-react';
import { motion } from 'framer-motion';
import { Notebook, NotebookDocument } from '../../types';
import {
  fetchNotebooks, createNotebook, deleteNotebook,
  fetchNotebookDocuments, uploadNotebookDocument, deleteNotebookDocument,
} from '../../services/notebookService';
import { useAuth } from '../../context/AuthContext';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { formatTimestamp } from '../../utils/chatUtils';
import { cn } from '../../lib/cn';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import IconButton from '../ui/IconButton';
import Button from '../ui/Button';
import { Card } from '../ui/Card';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard, Skeleton } from '../ui/Skeleton';
import PageHeader from '../ui/PageHeader';
import Input from '../ui/Input';
import Badge from '../ui/Badge';
import { useToast } from '../ui/Toast';
import { useConfirm } from '../ui/ConfirmDialog';
import NotebookChatPanel from './NotebookChatPanel';

const STATUS_ICON: Record<string, React.ReactNode> = {
  COMPLETED: <CheckCircle size={12} />,
  PROCESSING: <Clock size={12} className="animate-spin" />,
  PENDING: <Clock size={12} />,
  FAILED: <XCircle size={12} />,
  QUARANTINED: <AlertTriangle size={12} />,
};

const STATUS_BADGE: Record<string, 'success' | 'primary' | 'warning' | 'danger'> = {
  COMPLETED: 'success',
  PROCESSING: 'primary',
  PENDING: 'warning',
  FAILED: 'danger',
  QUARANTINED: 'danger',
};

const ALLOWED_EXTENSIONS = ['pdf', 'chm', 'html', 'htm', 'txt', 'md'];

const NotebooksPage: React.FC = () => {
  useDocumentTitle('Notebooks');
  const { token } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const confirm = useConfirm();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [documents, setDocuments] = useState<NotebookDocument[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  const loadNotebooks = () => {
    if (!token) return;
    return fetchNotebooks(token).then(res => {
      if (res.success && res.data) setNotebooks(res.data);
    });
  };

  useEffect(() => {
    if (!token) return;
    setIsLoading(true);
    loadNotebooks().finally(() => setIsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const selected = useMemo(() => notebooks.find(n => n.id === selectedId), [notebooks, selectedId]);
  const hasCompletedDocuments = documents.some(d => d.status === 'COMPLETED');
  const hasInFlightDocuments = documents.some(d => d.status === 'PENDING' || d.status === 'PROCESSING');

  const loadDocuments = (notebookId: string) => {
    if (!token) return;
    return fetchNotebookDocuments(notebookId, token).then(res => {
      if (res.success && res.data) setDocuments(res.data);
    });
  };

  const handleSelect = (id: string) => {
    setSelectedId(id);
    if (!token) return;
    setDocsLoading(true);
    loadDocuments(id).finally(() => setDocsLoading(false));
  };

  // While a document is still PENDING/PROCESSING, poll for its status the same way the admin
  // Documents tab does — otherwise a user watching their upload would see it stuck forever.
  useEffect(() => {
    if (!selectedId || !hasInFlightDocuments) return;
    const interval = setInterval(() => loadDocuments(selectedId), 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, hasInFlightDocuments]);

  const handleCreate = async () => {
    if (!token || !newName.trim()) return;
    setIsCreating(true);
    const res = await createNotebook(newName.trim(), newDesc.trim(), token);
    if (res.success && res.data) {
      setNotebooks(prev => [res.data!, ...prev]);
      setShowCreate(false);
      setNewName(''); setNewDesc('');
      toast.success('Notebook created');
    } else {
      toast.error(res.error ?? 'Failed to create notebook');
    }
    setIsCreating(false);
  };

  const handleDelete = async (id: string) => {
    if (!token) return;
    const ok = await confirm({
      title: 'Delete notebook?',
      message: 'This permanently deletes the notebook and every document you uploaded to it.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    const res = await deleteNotebook(id, token);
    if (res.success) {
      setNotebooks(prev => prev.filter(n => n.id !== id));
      if (selectedId === id) { setSelectedId(null); setDocuments([]); }
      toast.success('Notebook deleted');
    } else {
      toast.error(res.error ?? 'Failed to delete notebook');
    }
  };

  const handleUpload = async (file: File) => {
    if (!token || !selectedId) return;
    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      toast.error(`Unsupported file type. Allowed: ${ALLOWED_EXTENSIONS.join(', ')}`);
      return;
    }
    setIsUploading(true);
    const res = await uploadNotebookDocument(selectedId, file, token);
    if (res.success) {
      toast.success('Upload started — processing…');
      await loadDocuments(selectedId);
      await loadNotebooks();
    } else {
      toast.error(res.error ?? 'Upload failed');
    }
    setIsUploading(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleDeleteDocument = async (docId: string) => {
    if (!token || !selectedId) return;
    const res = await deleteNotebookDocument(selectedId, docId, token);
    if (res.success) {
      setDocuments(prev => prev.filter(d => d.id !== docId));
      loadNotebooks();
      toast.success('Document removed');
    } else {
      toast.error(res.error ?? 'Failed to remove document');
    }
  };

  return (
    <div className="flex h-full bg-background">
      {/* Left panel — notebook list */}
      <div className="w-72 bg-surface border-r border-border flex flex-col flex-shrink-0">
        <div className="flex items-center justify-between px-4 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <IconButton label="Go back" variant="ghost" size="sm" onClick={() => navigate(-1)}>
              <ArrowLeft size={16} />
            </IconButton>
            <h1 className="text-base font-semibold text-foreground">Notebooks</h1>
          </div>
          <IconButton label="New notebook" variant="ghost" size="sm" onClick={() => setShowCreate(true)}>
            <Plus size={16} />
          </IconButton>
        </div>

        {showCreate && (
          <div className="px-4 py-3 border-b border-border space-y-2 bg-primary/10">
            <Input
              type="text"
              aria-label="Notebook name"
              placeholder="Notebook name"
              value={newName}
              onChange={e => setNewName(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleCreate()}
              autoFocus
              className="py-1.5"
            />
            <Input
              type="text"
              aria-label="Notebook description"
              placeholder="Description (optional)"
              value={newDesc}
              onChange={e => setNewDesc(e.target.value)}
              className="py-1.5"
            />
            <div className="flex justify-end gap-1">
              <IconButton label="Cancel" variant="ghost" size="sm" onClick={() => setShowCreate(false)}>
                <X size={14} />
              </IconButton>
              <IconButton
                label="Create notebook"
                variant="primary"
                size="sm"
                onClick={handleCreate}
                disabled={isCreating || !newName.trim()}
              >
                <Check size={14} />
              </IconButton>
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto py-2">
          {isLoading ? (
            <div className="px-4 py-2 space-y-2">
              {[0, 1, 2].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          ) : notebooks.length === 0 ? (
            <EmptyState
              icon={NotebookText}
              title="No notebooks yet"
              description="Create one to upload your own documents and ask questions about them."
            />
          ) : (
            notebooks.map(n => (
              <button
                key={n.id}
                onClick={() => handleSelect(n.id)}
                className={cn(
                  'w-full flex items-start gap-3 px-4 py-3 text-left transition-colors',
                  selectedId === n.id ? 'bg-primary/10 border-r-2 border-primary' : 'hover:bg-surface-hover',
                )}
              >
                <div className={cn('mt-0.5', selectedId === n.id ? 'text-primary' : 'text-muted-foreground')}>
                  <NotebookText size={16} />
                </div>
                <div className="flex-1 min-w-0">
                  <span className="text-sm font-medium text-foreground truncate block">{n.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {n.documentCount} document{n.documentCount === 1 ? '' : 's'}
                  </span>
                </div>
                <IconButton
                  label="Delete notebook"
                  variant="ghost"
                  size="sm"
                  className="h-6 w-6 text-muted-foreground hover:text-danger flex-shrink-0"
                  onClick={e => { e.stopPropagation(); handleDelete(n.id); }}
                >
                  <Trash2 size={12} />
                </IconButton>
              </button>
            ))
          )}
        </div>
      </div>

      {/* Right panel — selected notebook: sources + chat */}
      <div className="flex-1 overflow-hidden">
        {!selected ? (
          <div className="flex h-full items-center justify-center">
            <EmptyState
              icon={BookOpen}
              title="No notebook selected"
              description="Select a notebook, or create one, to upload documents and start asking questions."
            />
          </div>
        ) : (
          <div className="h-full grid grid-cols-1 lg:grid-cols-[20rem_1fr]">
            {/* Sources column */}
            <div className="border-r border-border overflow-y-auto px-5 py-6">
              <PageHeader title={selected.name} description={selected.description} />

              <input
                ref={fileInputRef}
                type="file"
                accept={ALLOWED_EXTENSIONS.map(e => `.${e}`).join(',')}
                className="hidden"
                onChange={e => {
                  const file = e.target.files?.[0];
                  if (file) handleUpload(file);
                }}
              />
              <Button
                variant="outline"
                size="sm"
                className="w-full mb-4"
                leftIcon={<Upload size={14} />}
                loading={isUploading}
                onClick={() => fileInputRef.current?.click()}
              >
                Upload document
              </Button>

              {docsLoading ? (
                <div className="space-y-2">
                  {[0, 1].map(i => <SkeletonCard key={i} />)}
                </div>
              ) : documents.length === 0 ? (
                <EmptyState
                  icon={FileText}
                  title="No documents yet"
                  description="Upload a PDF, Word, HTML, CHM, or text file to get started."
                />
              ) : (
                <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-2">
                  {documents.map(doc => (
                    <motion.div key={doc.id} variants={fadeInUp}>
                      <Card className="p-3">
                        <div className="flex items-start gap-2">
                          <FileText size={14} className="mt-0.5 text-muted-foreground flex-shrink-0" />
                          <div className="flex-1 min-w-0">
                            <p className="text-sm text-foreground truncate">{doc.documentName}</p>
                            <div className="flex items-center gap-2 mt-1">
                              <Badge variant={STATUS_BADGE[doc.status] ?? 'neutral'}>
                                {STATUS_ICON[doc.status]}
                                {doc.status}
                              </Badge>
                              {doc.createdAt && (
                                <span className="text-xs text-muted-foreground">
                                  {formatTimestamp(new Date(doc.createdAt).getTime())}
                                </span>
                              )}
                            </div>
                            {doc.status === 'FAILED' && doc.errorMessage && (
                              <p className="text-xs text-danger mt-1">{doc.errorMessage}</p>
                            )}
                          </div>
                          <IconButton
                            label="Remove document"
                            variant="ghost"
                            size="sm"
                            className="h-6 w-6 text-muted-foreground hover:text-danger flex-shrink-0"
                            onClick={() => handleDeleteDocument(doc.id)}
                          >
                            <Trash2 size={12} />
                          </IconButton>
                        </div>
                      </Card>
                    </motion.div>
                  ))}
                </motion.div>
              )}
            </div>

            {/* Chat column */}
            <div className="overflow-hidden px-5 py-6">
              <NotebookChatPanel notebookId={selected.id} hasCompletedDocuments={hasCompletedDocuments} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default NotebooksPage;
