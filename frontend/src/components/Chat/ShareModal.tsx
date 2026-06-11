import React, { useEffect, useState } from 'react';
import { X, Share2, Copy, Check, Trash2, Globe, Lock } from 'lucide-react';
import { ShareLink } from '../../types';
import {
  createShareLink,
  getShareLink,
  deleteShareLink,
} from '../../services/shareService';
import { useAuth } from '../../context/AuthContext';

interface ShareModalProps {
  chatId: string;
  onClose: () => void;
}

const EXPIRE_OPTIONS = [
  { label: '7 days', value: 7 },
  { label: '30 days', value: 30 },
  { label: 'Never', value: null },
];

const ShareModal: React.FC<ShareModalProps> = ({ chatId, onClose }) => {
  const { token } = useAuth();
  const [link, setLink] = useState<ShareLink | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [copied, setCopied] = useState(false);
  const [publicAccess, setPublicAccess] = useState(false);
  const [expireDays, setExpireDays] = useState<number | null>(7);

  useEffect(() => {
    if (!token) return;
    getShareLink(chatId, token).then(res => {
      if (res.success && res.data) setLink(res.data);
    }).finally(() => setIsLoading(false));
  }, [chatId, token]);

  const shareUrl = link
    ? `${window.location.origin}/share/${link.token}`
    : '';

  const handleCreate = async () => {
    if (!token) return;
    setIsCreating(true);
    const res = await createShareLink(chatId, publicAccess, expireDays, token);
    if (res.success && res.data) setLink(res.data);
    setIsCreating(false);
  };

  const handleDelete = async () => {
    if (!token) return;
    await deleteShareLink(chatId, token);
    setLink(null);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(shareUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <Share2 size={18} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Share Chat</h2>
          </div>
          <button onClick={onClose} className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
            <X size={16} />
          </button>
        </div>

        <div className="px-6 py-5 space-y-4">
          {isLoading ? (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
          ) : link ? (
            <>
              <div className="flex items-center gap-2 p-3 bg-gray-50 rounded-xl border border-gray-200">
                <span className="flex-1 text-sm text-gray-700 font-mono truncate">{shareUrl}</span>
                <button
                  onClick={handleCopy}
                  className="flex items-center gap-1 px-3 py-1.5 text-xs bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex-shrink-0"
                >
                  {copied ? <><Check size={12} /> Copied</> : <><Copy size={12} /> Copy</>}
                </button>
              </div>

              <div className="flex items-center gap-2 text-sm text-gray-500">
                {link.publicAccess
                  ? <><Globe size={14} className="text-green-500" /> Anyone with the link can view</>
                  : <><Lock size={14} className="text-gray-400" /> Only signed-in users can view</>
                }
                {link.expiresAt && (
                  <span className="ml-auto text-xs">
                    Expires {new Date(link.expiresAt).toLocaleDateString()}
                  </span>
                )}
              </div>

              <button
                onClick={handleDelete}
                className="flex items-center gap-2 text-sm text-red-500 hover:text-red-700 transition-colors"
              >
                <Trash2 size={14} /> Revoke link
              </button>
            </>
          ) : (
            <>
              <p className="text-sm text-gray-600">
                Create a shareable link so teammates can read this conversation. They can also fork it into their own account.
              </p>

              <div>
                <label className="block text-xs font-medium text-gray-600 mb-2">Access</label>
                <div className="grid grid-cols-2 gap-2">
                  {[
                    { value: false, icon: Lock, label: 'Team only', desc: 'Signed-in users' },
                    { value: true, icon: Globe, label: 'Public', desc: 'Anyone with link' },
                  ].map(opt => (
                    <button
                      key={String(opt.value)}
                      onClick={() => setPublicAccess(opt.value)}
                      className={`flex flex-col items-center p-3 rounded-xl border-2 text-xs transition-all ${
                        publicAccess === opt.value
                          ? 'border-blue-500 bg-blue-50 text-blue-700'
                          : 'border-gray-200 text-gray-600 hover:border-gray-300'
                      }`}
                    >
                      <opt.icon size={16} className="mb-1" />
                      <span className="font-medium">{opt.label}</span>
                      <span className="opacity-70">{opt.desc}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-600 mb-2">Expires</label>
                <div className="flex gap-2">
                  {EXPIRE_OPTIONS.map(opt => (
                    <button
                      key={String(opt.value)}
                      onClick={() => setExpireDays(opt.value)}
                      className={`flex-1 py-2 rounded-lg border text-xs font-medium transition-colors ${
                        expireDays === opt.value
                          ? 'border-blue-500 bg-blue-50 text-blue-700'
                          : 'border-gray-200 text-gray-600 hover:border-gray-300'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>

        {!link && !isLoading && (
          <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100">
            <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              Cancel
            </button>
            <button
              onClick={handleCreate}
              disabled={isCreating}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors font-medium"
            >
              {isCreating ? 'Creating…' : 'Create link'}
            </button>
          </div>
        )}
        {link && (
          <div className="flex justify-end px-6 py-4 border-t border-gray-100">
            <button onClick={onClose} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium">
              Done
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default ShareModal;
