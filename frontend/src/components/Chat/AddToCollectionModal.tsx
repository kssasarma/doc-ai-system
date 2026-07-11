import React, { useEffect, useState } from 'react';
import { FolderPlus, Check, Folder, Globe, Lock } from 'lucide-react';
import { Collection } from '../../types';
import { fetchCollections, addToCollection } from '../../services/collectionService';
import { useAuth } from '../../context/AuthContext';
import Modal, { ModalBody, ModalFooter } from '../ui/Modal';
import Button from '../ui/Button';
import Input from '../ui/Input';
import Spinner from '../ui/Spinner';
import { cn } from '../../lib/cn';

interface AddToCollectionModalProps {
  messageId: string;
  chatId: string;
  onClose: () => void;
}

const AddToCollectionModal: React.FC<AddToCollectionModalProps> = ({ messageId, chatId, onClose }) => {
  const { token } = useAuth();
  const [collections, setCollections] = useState<Collection[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [note, setNote] = useState('');
  const [selectedCollectionId, setSelectedCollectionId] = useState<string | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    fetchCollections(token).then(res => {
      if (res.success && res.data) setCollections(res.data);
    }).finally(() => setIsLoading(false));
  }, [token]);

  const handleAdd = async () => {
    if (!token || !selectedCollectionId) return;
    setIsAdding(true);
    setError(null);
    const res = await addToCollection(selectedCollectionId, messageId, chatId, note, token);
    if (res.success) {
      setAdded(true);
      setTimeout(onClose, 1000);
    } else {
      setError(res.error || 'Failed to add to collection');
    }
    setIsAdding(false);
  };

  return (
    <Modal open onClose={onClose} title="Add to collection" size="sm" icon={<FolderPlus size={18} className="text-primary" />}>
      <ModalBody className="space-y-3">
        {isLoading ? (
          <div className="flex items-center justify-center py-6">
            <Spinner size="md" />
          </div>
        ) : collections.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            No collections yet. Create one on the Collections page.
          </p>
        ) : (
          <div className="space-y-1 max-h-48 overflow-y-auto">
            {collections.map(c => (
              <button
                key={c.id}
                onClick={() => setSelectedCollectionId(c.id)}
                className={cn(
                  'w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-colors border',
                  selectedCollectionId === c.id ? 'bg-primary/10 border-primary/30' : 'hover:bg-surface-hover border-transparent',
                )}
              >
                <Folder size={15} className={selectedCollectionId === c.id ? 'text-primary' : 'text-muted-foreground'} />
                <span className="flex-1 text-sm font-medium text-foreground truncate">{c.name}</span>
                <span className="flex-shrink-0">
                  {c.publicAccess
                    ? <Globe size={11} className="text-success" />
                    : <Lock size={11} className="text-muted-foreground" />}
                </span>
                {selectedCollectionId === c.id && <Check size={14} className="text-primary flex-shrink-0" />}
              </button>
            ))}
          </div>
        )}

        {selectedCollectionId && (
          <Input
            type="text"
            placeholder="Add a note (optional)"
            value={note}
            onChange={e => setNote(e.target.value)}
          />
        )}

        {error && <p className="text-xs text-danger">{error}</p>}
      </ModalBody>

      <ModalFooter>
        <Button variant="ghost" size="sm" onClick={onClose}>Cancel</Button>
        <Button size="sm" onClick={handleAdd} disabled={!selectedCollectionId || isAdding || added} leftIcon={added ? <Check size={13} /> : undefined}>
          {added ? 'Added!' : isAdding ? 'Adding…' : 'Add to collection'}
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default AddToCollectionModal;
