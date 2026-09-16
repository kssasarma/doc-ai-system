import { useCallback, useEffect, useState } from 'react';
import { ChunkReview } from '../../types';
import { fetchDocumentChunks, excludeChunk, includeChunk } from '../../services/piiFlagService';
import { ScanEye, RotateCcw, ShieldAlert } from 'lucide-react';
import Modal, { ModalBody } from '../ui/Modal';
import Button from '../ui/Button';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';
import { useConfirm } from '../ui/ConfirmDialog';

/** Per-document chunk browser for PII review — lets an admin exclude (or restore) any individual
 * chunk from search, independent of quarantining/rejecting the whole document. Opened from
 * DocumentsTab; also where a chunk excluded from the PII Review queue (see PiiFlagsTab) can later
 * be restored. */
export default function ChunkReviewModal({
  documentId, documentName, token, onClose,
}: {
  documentId: string;
  documentName: string;
  token: string;
  onClose: () => void;
}) {
  const toast = useToast();
  const confirm = useConfirm();
  const [chunks, setChunks] = useState<ChunkReview[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [actioningId, setActioningId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    const res = await fetchDocumentChunks(documentId, token);
    if (res.success && res.data) setChunks(res.data);
    else toast.error(res.error ?? 'Failed to load chunks.');
    setLoading(false);
  }, [documentId, token, toast]);

  useEffect(() => { load(); }, [load]);

  const handleExclude = async (chunk: ChunkReview) => {
    const confirmed = await confirm({
      title: 'Reject chunk',
      message: `Exclude chunk #${chunk.chunkIndex} from search? It stops appearing in answers and citations; the rest of the document stays searchable.`,
      confirmLabel: 'Reject chunk',
      danger: true,
    });
    if (!confirmed) return;
    setActioningId(chunk.id);
    const res = await excludeChunk(chunk.id, token);
    if (res.success) {
      setChunks(prev => prev?.map(c => c.id === chunk.id
        ? { ...c, excludedFromSearch: true, exclusionReason: 'PII' } : c) ?? null);
      toast.success(`Chunk #${chunk.chunkIndex} excluded from search.`);
    } else {
      toast.error(res.error ?? 'Failed to exclude chunk.');
    }
    setActioningId(null);
  };

  const handleInclude = async (chunk: ChunkReview) => {
    setActioningId(chunk.id);
    const res = await includeChunk(chunk.id, token);
    if (res.success) {
      setChunks(prev => prev?.map(c => c.id === chunk.id
        ? { ...c, excludedFromSearch: false, exclusionReason: null } : c) ?? null);
      toast.success(`Chunk #${chunk.chunkIndex} restored to search.`);
    } else {
      toast.error(res.error ?? 'Failed to restore chunk.');
    }
    setActioningId(null);
  };

  return (
    <Modal open onClose={onClose} title={`Chunks — ${documentName}`} icon={<ScanEye size={15} className="text-primary" />} size="lg">
      <ModalBody>
        {loading ? (
          <div className="space-y-2">
            <SkeletonRow columns={3} />
            <SkeletonRow columns={3} />
            <SkeletonRow columns={3} />
          </div>
        ) : !chunks || chunks.length === 0 ? (
          <EmptyState icon={ScanEye} title="No chunks" description="This document has no processed chunks yet." />
        ) : (
          <div className="space-y-3 max-h-[60vh] overflow-y-auto">
            {chunks.map(chunk => (
              <div key={chunk.id} className="rounded-lg border border-border p-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs font-mono text-muted-foreground">#{chunk.chunkIndex}</span>
                      <Badge variant="neutral">{chunk.chunkType}</Badge>
                      {!chunk.isLeaf && <Badge variant="neutral">Section</Badge>}
                      {chunk.excludedFromSearch && (
                        <Badge variant="danger">Excluded ({chunk.exclusionReason ?? 'PII'})</Badge>
                      )}
                      {chunk.piiFlags.length > 0 && (
                        <span className="flex items-center gap-1 text-xs text-warning">
                          <ShieldAlert className="w-3 h-3" />
                          {chunk.piiFlags.map(f => f.piiType).join(', ')}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground font-mono mt-1.5 whitespace-pre-wrap break-words">
                      {chunk.contentPreview}
                    </p>
                  </div>
                  <div className="flex-shrink-0">
                    {chunk.excludedFromSearch ? (
                      <Button
                        variant="outline"
                        size="sm"
                        loading={actioningId === chunk.id}
                        leftIcon={<RotateCcw className="w-3 h-3" />}
                        onClick={() => handleInclude(chunk)}
                      >
                        Restore
                      </Button>
                    ) : (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={actioningId === chunk.id}
                        leftIcon={<ScanEye className="w-3 h-3" />}
                        onClick={() => handleExclude(chunk)}
                      >
                        Reject
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </ModalBody>
    </Modal>
  );
}
