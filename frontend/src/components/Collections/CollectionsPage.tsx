import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft, Plus, Folder, FolderOpen, Globe, Lock, Trash2,
  MessageSquare, Users, ChevronRight, X, Check
} from 'lucide-react';
import { Collection, CollectionItem } from '../../types';
import {
  fetchCollections, createCollection, deleteCollection,
  fetchCollectionItems, removeFromCollection
} from '../../services/collectionService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';

const CollectionsPage: React.FC = () => {
  const { token } = useAuth();
  const navigate = useNavigate();
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
    }
    setIsCreating(false);
  };

  const handleDelete = async (id: string) => {
    if (!token) return;
    const res = await deleteCollection(id, token);
    if (res.success) {
      setCollections(prev => prev.filter(c => c.id !== id));
      if (selectedId === id) { setSelectedId(null); setItems([]); }
    }
  };

  const handleRemoveItem = async (itemId: string) => {
    if (!token || !selectedId) return;
    const res = await removeFromCollection(selectedId, itemId, token);
    if (res.success) setItems(prev => prev.filter(i => i.id !== itemId));
  };

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Left panel — collection list */}
      <div className="w-72 bg-white border-r border-gray-200 flex flex-col">
        <div className="flex items-center justify-between px-4 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <button onClick={() => navigate(-1)} className="p-1.5 text-gray-400 hover:text-gray-600 rounded-lg transition-colors">
              <ArrowLeft size={16} />
            </button>
            <h1 className="text-base font-semibold text-gray-900">Collections</h1>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
            title="New collection"
          >
            <Plus size={16} />
          </button>
        </div>

        {/* Create form */}
        {showCreate && (
          <div className="px-4 py-3 border-b border-gray-100 space-y-2 bg-blue-50">
            <input
              type="text"
              placeholder="Collection name"
              value={newName}
              onChange={e => setNewName(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleCreate()}
              autoFocus
              className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <input
              type="text"
              placeholder="Description (optional)"
              value={newDesc}
              onChange={e => setNewDesc(e.target.value)}
              className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <div className="flex items-center justify-between">
              <button
                onClick={() => setNewPublic(!newPublic)}
                className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full transition-colors ${
                  newPublic ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'
                }`}
              >
                {newPublic ? <><Globe size={11} /> Public</> : <><Lock size={11} /> Private</>}
              </button>
              <div className="flex gap-1">
                <button onClick={() => setShowCreate(false)} className="p-1.5 text-gray-400 hover:text-gray-600 rounded transition-colors">
                  <X size={14} />
                </button>
                <button
                  onClick={handleCreate}
                  disabled={isCreating || !newName.trim()}
                  className="p-1.5 text-blue-600 hover:bg-blue-100 rounded transition-colors disabled:opacity-40"
                >
                  <Check size={14} />
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto py-2">
          {isLoading ? (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600" />
            </div>
          ) : collections.length === 0 ? (
            <div className="text-center py-10 text-gray-400 text-sm px-4">
              <Folder size={32} className="mx-auto mb-2 text-gray-300" />
              No collections yet. Create one to start curating answers.
            </div>
          ) : (
            collections.map(c => (
              <button
                key={c.id}
                onClick={() => handleSelect(c.id)}
                className={`w-full flex items-start gap-3 px-4 py-3 text-left transition-colors ${
                  selectedId === c.id ? 'bg-blue-50 border-r-2 border-blue-500' : 'hover:bg-gray-50'
                }`}
              >
                <div className={`mt-0.5 ${selectedId === c.id ? 'text-blue-600' : 'text-gray-400'}`}>
                  {selectedId === c.id ? <FolderOpen size={16} /> : <Folder size={16} />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-sm font-medium text-gray-800 truncate">{c.name}</span>
                    {c.publicAccess
                      ? <Globe size={10} className="text-green-500 flex-shrink-0" />
                      : <Lock size={10} className="text-gray-400 flex-shrink-0" />}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs text-gray-400">{c.itemCount} items</span>
                    {!c.isOwner && <span className="text-xs text-blue-500 flex items-center gap-0.5"><Users size={9} /> {c.createdByUsername}</span>}
                  </div>
                </div>
                {c.isOwner && (
                  <button
                    onClick={e => { e.stopPropagation(); handleDelete(c.id); }}
                    className="p-1 text-gray-300 hover:text-red-400 rounded transition-colors flex-shrink-0"
                  >
                    <Trash2 size={12} />
                  </button>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Right panel — items */}
      <div className="flex-1 overflow-y-auto">
        {!selected ? (
          <div className="flex h-full items-center justify-center text-gray-400">
            <div className="text-center space-y-2">
              <FolderOpen size={48} className="mx-auto text-gray-200" />
              <p className="text-sm">Select a collection to view its items</p>
            </div>
          </div>
        ) : (
          <div className="max-w-2xl mx-auto px-6 py-8">
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-xl font-semibold text-gray-900">{selected.name}</h2>
                {selected.description && <p className="text-sm text-gray-500 mt-0.5">{selected.description}</p>}
              </div>
              <span className="text-sm text-gray-400">{selected.itemCount} items</span>
            </div>

            {itemsLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600" />
              </div>
            ) : items.length === 0 ? (
              <div className="text-center py-16 text-gray-400 text-sm">
                <MessageSquare size={32} className="mx-auto mb-2 text-gray-200" />
                No items yet. Bookmark answers to add them to collections.
              </div>
            ) : (
              <div className="space-y-3">
                {items.map(item => (
                  <div key={item.id} className="bg-white border border-gray-200 rounded-xl p-4 hover:border-blue-200 transition-colors">
                    <div className="flex items-start gap-3">
                      <div className="flex-1 min-w-0">
                        {item.messageContent && (
                          <p className="text-sm text-gray-700 line-clamp-3 leading-relaxed">
                            {item.messageContent}
                          </p>
                        )}
                        {item.note && (
                          <p className="text-xs text-blue-700 bg-blue-50 rounded px-2 py-1 mt-2 italic">
                            {item.note}
                          </p>
                        )}
                        <div className="flex items-center gap-3 mt-2">
                          {item.addedByUsername && (
                            <span className="text-xs text-gray-400">by {item.addedByUsername}</span>
                          )}
                          <span className="text-xs text-gray-400">
                            {formatTimestamp(new Date(item.createdAt).getTime())}
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 flex-shrink-0">
                        <button
                          onClick={() => navigate(`/chat/${item.chatId}`)}
                          title="Open original chat"
                          className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        >
                          <ChevronRight size={14} />
                        </button>
                        {selected.isOwner && (
                          <button
                            onClick={() => handleRemoveItem(item.id)}
                            title="Remove from collection"
                            className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                          >
                            <Trash2 size={14} />
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default CollectionsPage;
