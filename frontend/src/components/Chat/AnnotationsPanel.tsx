import React, { useEffect, useState } from 'react';
import { Plus, Trash2, Send } from 'lucide-react';
import { ChunkAnnotation } from '../../types';
import { fetchAnnotations, createAnnotation, deleteAnnotation } from '../../services/annotationService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';

interface AnnotationsPanelProps {
  chunkId: string;
}

const AnnotationsPanel: React.FC<AnnotationsPanelProps> = ({ chunkId }) => {
  const { token, user } = useAuth();
  const [annotations, setAnnotations] = useState<ChunkAnnotation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showInput, setShowInput] = useState(false);
  const [newBody, setNewBody] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!token) return;
    fetchAnnotations(chunkId, token).then(res => {
      if (res.success && res.data) setAnnotations(res.data);
    }).finally(() => setIsLoading(false));
  }, [chunkId, token]);

  const handleAdd = async () => {
    if (!token || !newBody.trim()) return;
    setIsSubmitting(true);
    const res = await createAnnotation(chunkId, newBody.trim(), token);
    if (res.success && res.data) {
      setAnnotations(prev => [...prev, res.data!]);
      setNewBody('');
      setShowInput(false);
    }
    setIsSubmitting(false);
  };

  const handleDelete = async (annotationId: string) => {
    if (!token) return;
    const res = await deleteAnnotation(chunkId, annotationId, token);
    if (res.success) setAnnotations(prev => prev.filter(a => a.id !== annotationId));
  };

  return (
    <div className="mt-2 border-t border-border pt-2 space-y-2">
      {isLoading ? (
        <p className="text-muted-foreground text-xs">Loading notes…</p>
      ) : (
        <>
          {annotations.length === 0 && !showInput && (
            <p className="text-muted-foreground text-xs italic">No team notes yet on this source.</p>
          )}
          {annotations.map(a => (
            <div key={a.id} className="bg-accent/5 border border-accent/15 rounded-lg px-2.5 py-2 text-xs">
              <div className="flex items-start justify-between gap-2">
                <p className="text-foreground flex-1">{a.body}</p>
                {user && a.userId === user.userId && (
                  <button
                    onClick={() => handleDelete(a.id)}
                    aria-label="Delete note"
                    className="text-muted-foreground/50 hover:text-danger transition-colors flex-shrink-0"
                  >
                    <Trash2 size={11} />
                  </button>
                )}
              </div>
              <p className="text-muted-foreground mt-1">
                @{a.username} · {formatTimestamp(new Date(a.createdAt).getTime())}
              </p>
            </div>
          ))}

          {showInput ? (
            <div className="flex gap-1.5">
              <input
                type="text"
                placeholder="Add a note…"
                value={newBody}
                onChange={e => setNewBody(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleAdd()}
                autoFocus
                className="flex-1 px-2 py-1.5 text-xs bg-surface border border-accent/25 rounded-lg text-foreground focus:outline-none focus:ring-1 focus:ring-accent"
              />
              <button
                onClick={handleAdd}
                disabled={isSubmitting || !newBody.trim()}
                aria-label="Send note"
                className="p-1.5 text-accent hover:bg-accent/10 rounded-lg transition-colors disabled:opacity-40"
              >
                <Send size={12} />
              </button>
            </div>
          ) : (
            <button
              onClick={() => setShowInput(true)}
              className="flex items-center gap-1 text-xs text-accent hover:opacity-80 transition-opacity"
            >
              <Plus size={11} /> Add note
            </button>
          )}
        </>
      )}
    </div>
  );
};

export default AnnotationsPanel;
