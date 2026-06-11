import React, { useEffect, useState } from 'react';
import { X, FolderPlus, Check, Folder, Globe, Lock } from 'lucide-react';
import { Collection } from '../../types';
import { fetchCollections, addToCollection } from '../../services/collectionService';
import { useAuth } from '../../context/AuthContext';

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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <FolderPlus size={18} className="text-blue-600" />
            <h2 className="text-base font-semibold text-gray-900">Add to collection</h2>
          </div>
          <button onClick={onClose} className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
            <X size={15} />
          </button>
        </div>

        <div className="px-5 py-4 space-y-3">
          {isLoading ? (
            <div className="flex items-center justify-center py-6">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600" />
            </div>
          ) : collections.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-4">
              No collections yet. Create one on the Collections page.
            </p>
          ) : (
            <div className="space-y-1 max-h-48 overflow-y-auto">
              {collections.map(c => (
                <button
                  key={c.id}
                  onClick={() => setSelectedCollectionId(c.id)}
                  className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-colors ${
                    selectedCollectionId === c.id
                      ? 'bg-blue-50 border border-blue-200'
                      : 'hover:bg-gray-50 border border-transparent'
                  }`}
                >
                  <Folder size={15} className={selectedCollectionId === c.id ? 'text-blue-600' : 'text-gray-400'} />
                  <span className="flex-1 text-sm font-medium text-gray-800 truncate">{c.name}</span>
                  <span className="flex-shrink-0">
                    {c.publicAccess
                      ? <Globe size={11} className="text-green-500" />
                      : <Lock size={11} className="text-gray-400" />}
                  </span>
                  {selectedCollectionId === c.id && <Check size={14} className="text-blue-600 flex-shrink-0" />}
                </button>
              ))}
            </div>
          )}

          {selectedCollectionId && (
            <input
              type="text"
              placeholder="Add a note (optional)"
              value={note}
              onChange={e => setNote(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          )}

          {error && <p className="text-xs text-red-500">{error}</p>}
        </div>

        <div className="flex items-center justify-end gap-3 px-5 py-3 border-t border-gray-100">
          <button onClick={onClose} className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
            Cancel
          </button>
          <button
            onClick={handleAdd}
            disabled={!selectedCollectionId || isAdding || added}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors font-medium"
          >
            {added ? <><Check size={13} /> Added!</> : isAdding ? 'Adding…' : 'Add to collection'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AddToCollectionModal;
