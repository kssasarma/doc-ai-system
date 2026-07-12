import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft, Plus, Folder, FolderOpen, Globe, Lock, Trash2,
  MessageSquare, Users, ChevronRight, X, Check
} from 'lucide-react';
import { motion } from 'framer-motion';
import { Collection, CollectionItem } from '../../types';
import {
  fetchCollections, createCollection, deleteCollection,
  fetchCollectionItems, removeFromCollection
} from '../../services/collectionService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';
import { cn } from '../../lib/cn';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import IconButton from '../ui/IconButton';
import { Card } from '../ui/Card';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard, Skeleton } from '../ui/Skeleton';
import PageHeader from '../ui/PageHeader';
import Input from '../ui/Input';
import { useToast } from '../ui/Toast';

const CollectionsPage: React.FC = () => {
  const { token } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [collections, setCollections] = useState<Collection[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [items, setItems] = useState<CollectionItem[]>([]);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newPublic, setNewPublic] = useState(true);
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    if (!token) return;
    fetchCollections(token).then(res => {
      if (res.success && res.data) setCollections(res.data);
    }).finally(() => setIsLoading(false));
  }, [token]);

  const selected = useMemo(() => collections.find(c => c.id === selectedId), [collections, selectedId]);

  const handleSelect = async (id: string) => {
    setSelectedId(id);
    if (!token) return;
    setItemsLoading(true);
    fetchCollectionItems(id, token).then(res => {
      if (res.success && res.data) setItems(res.data);
    }).finally(() => setItemsLoading(false));
  };

  const handleCreate = async () => {
    if (!token || !newName.trim()) return;
    setIsCreating(true);
    const res = await createCollection(newName.trim(), newDesc.trim(), newPublic, token);
    if (res.success && res.data) {
      setCollections(prev => [res.data!, ...prev]);
      setShowCreate(false);
      setNewName(''); setNewDesc('');
      toast.success('Collection created');
    } else {
      toast.error(res.error ?? 'Failed to create collection');
    }
    setIsCreating(false);
  };

  const handleDelete = async (id: string) => {
    if (!token) return;
    const res = await deleteCollection(id, token);
    if (res.success) {
      setCollections(prev => prev.filter(c => c.id !== id));
      if (selectedId === id) { setSelectedId(null); setItems([]); }
      toast.success('Collection deleted');
    } else {
      toast.error(res.error ?? 'Failed to delete collection');
    }
  };

  const handleRemoveItem = async (itemId: string) => {
    if (!token || !selectedId) return;
    const res = await removeFromCollection(selectedId, itemId, token);
    if (res.success) {
      setItems(prev => prev.filter(i => i.id !== itemId));
      toast.success('Removed from collection');
    } else {
      toast.error(res.error ?? 'Failed to remove item');
    }
  };

  return (
    <div className="flex h-full bg-background">
      {/* Left panel — collection list */}
      <div className="w-72 bg-surface border-r border-border flex flex-col">
        <div className="flex items-center justify-between px-4 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <IconButton label="Go back" variant="ghost" size="sm" onClick={() => navigate(-1)}>
              <ArrowLeft size={16} />
            </IconButton>
            <h1 className="text-base font-semibold text-foreground">Collections</h1>
          </div>
          <IconButton label="New collection" variant="ghost" size="sm" onClick={() => setShowCreate(true)}>
            <Plus size={16} />
          </IconButton>
        </div>

        {/* Create form */}
        {showCreate && (
          <div className="px-4 py-3 border-b border-border space-y-2 bg-primary/10">
            <Input
              type="text"
              placeholder="Collection name"
              value={newName}
              onChange={e => setNewName(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleCreate()}
              autoFocus
              className="py-1.5"
            />
            <Input
              type="text"
              placeholder="Description (optional)"
              value={newDesc}
              onChange={e => setNewDesc(e.target.value)}
              className="py-1.5"
            />
            <div className="flex items-center justify-between">
              <button
                onClick={() => setNewPublic(!newPublic)}
                className={cn(
                  'flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full transition-colors',
                  newPublic ? 'bg-success/10 text-success' : 'bg-muted text-muted-foreground',
                )}
              >
                {newPublic ? <><Globe size={11} /> Public</> : <><Lock size={11} /> Private</>}
              </button>
              <div className="flex gap-1">
                <IconButton label="Cancel" variant="ghost" size="sm" onClick={() => setShowCreate(false)}>
                  <X size={14} />
                </IconButton>
                <IconButton
                  label="Create collection"
                  variant="primary"
                  size="sm"
                  onClick={handleCreate}
                  disabled={isCreating || !newName.trim()}
                >
                  <Check size={14} />
                </IconButton>
              </div>
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto py-2">
          {isLoading ? (
            <div className="px-4 py-2 space-y-2">
              {[0, 1, 2].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          ) : collections.length === 0 ? (
            <EmptyState
              icon={Folder}
              title="No collections yet"
              description="Create one to start curating answers."
            />
          ) : (
            collections.map(c => (
              <button
                key={c.id}
                onClick={() => handleSelect(c.id)}
                className={cn(
                  'w-full flex items-start gap-3 px-4 py-3 text-left transition-colors',
                  selectedId === c.id ? 'bg-primary/10 border-r-2 border-primary' : 'hover:bg-surface-hover',
                )}
              >
                <div className={cn('mt-0.5', selectedId === c.id ? 'text-primary' : 'text-muted-foreground')}>
                  {selectedId === c.id ? <FolderOpen size={16} /> : <Folder size={16} />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-sm font-medium text-foreground truncate">{c.name}</span>
                    {c.publicAccess
                      ? <Globe size={10} className="text-success flex-shrink-0" />
                      : <Lock size={10} className="text-muted-foreground flex-shrink-0" />}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs text-muted-foreground">{c.itemCount} items</span>
                    {!c.isOwner && <span className="text-xs text-primary flex items-center gap-0.5"><Users size={9} /> {c.createdByUsername}</span>}
                  </div>
                </div>
                {c.isOwner && (
                  <IconButton
                    label="Delete collection"
                    variant="ghost"
                    size="sm"
                    className="h-6 w-6 text-muted-foreground hover:text-danger flex-shrink-0"
                    onClick={e => { e.stopPropagation(); handleDelete(c.id); }}
                  >
                    <Trash2 size={12} />
                  </IconButton>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Right panel — items */}
      <div className="flex-1 overflow-y-auto">
        {!selected ? (
          <div className="flex h-full items-center justify-center">
            <EmptyState
              icon={FolderOpen}
              title="No collection selected"
              description="Select a collection from the list to view its items."
            />
          </div>
        ) : (
          <div className="max-w-2xl mx-auto px-6 py-8">
            <PageHeader
              title={selected.name}
              description={selected.description}
              actions={<span className="text-sm text-muted-foreground">{selected.itemCount} items</span>}
            />

            {itemsLoading ? (
              <div className="space-y-3">
                {[0, 1, 2].map(i => <SkeletonCard key={i} />)}
              </div>
            ) : items.length === 0 ? (
              <EmptyState
                icon={MessageSquare}
                title="No items yet"
                description="Bookmark answers to add them to collections."
              />
            ) : (
              <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-3">
                {items.map(item => (
                  <motion.div key={item.id} variants={fadeInUp}>
                    <Card className="p-4 hover:border-primary/30 transition-colors">
                      <div className="flex items-start gap-3">
                        <div className="flex-1 min-w-0">
                          {item.messageContent && (
                            <p className="text-sm text-foreground line-clamp-3 leading-relaxed">
                              {item.messageContent}
                            </p>
                          )}
                          {item.note && (
                            <p className="text-xs text-primary bg-primary/10 rounded px-2 py-1 mt-2 italic">
                              {item.note}
                            </p>
                          )}
                          <div className="flex items-center gap-3 mt-2">
                            {item.addedByUsername && (
                              <span className="text-xs text-muted-foreground">by {item.addedByUsername}</span>
                            )}
                            <span className="text-xs text-muted-foreground">
                              {formatTimestamp(new Date(item.createdAt).getTime())}
                            </span>
                          </div>
                        </div>
                        <div className="flex items-center gap-1 flex-shrink-0">
                          <IconButton
                            label="Open original chat"
                            variant="ghost"
                            size="sm"
                            onClick={() => navigate(`/chat/${item.chatId}`)}
                          >
                            <ChevronRight size={14} />
                          </IconButton>
                          {selected.isOwner && (
                            <IconButton
                              label="Remove from collection"
                              variant="danger"
                              size="sm"
                              onClick={() => handleRemoveItem(item.id)}
                            >
                              <Trash2 size={14} />
                            </IconButton>
                          )}
                        </div>
                      </div>
                    </Card>
                  </motion.div>
                ))}
              </motion.div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default CollectionsPage;
