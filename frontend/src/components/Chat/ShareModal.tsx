import React, { useEffect, useState } from 'react';
import { Share2, Copy, Check, Trash2, Globe, Lock } from 'lucide-react';
import { ShareLink } from '../../types';
import {
  createShareLink,
  getShareLink,
  deleteShareLink,
} from '../../services/shareService';
import { useAuth } from '../../context/AuthContext';
import Modal, { ModalBody, ModalFooter } from '../ui/Modal';
import Button from '../ui/Button';
import Spinner from '../ui/Spinner';
import { cn } from '../../lib/cn';

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
    <Modal open onClose={onClose} title="Share Chat" icon={<Share2 size={18} className="text-primary" />}>
      <ModalBody className="space-y-4">
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <Spinner size="lg" />
          </div>
        ) : link ? (
          <>
            <div className="flex items-center gap-2 p-3 bg-muted rounded-xl border border-border">
              <span className="flex-1 text-sm text-foreground font-mono truncate">{shareUrl}</span>
              <Button size="sm" onClick={handleCopy} leftIcon={copied ? <Check size={12} /> : <Copy size={12} />} className="flex-shrink-0">
                {copied ? 'Copied' : 'Copy'}
              </Button>
            </div>

            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              {link.publicAccess
                ? <><Globe size={14} className="text-success" /> Anyone with the link can view</>
                : <><Lock size={14} className="text-muted-foreground" /> Only signed-in users can view</>
              }
              {link.expiresAt && (
                <span className="ml-auto text-xs">
                  Expires {new Date(link.expiresAt).toLocaleDateString()}
                </span>
              )}
            </div>

            <button
              onClick={handleDelete}
              className="flex items-center gap-2 text-sm text-danger hover:text-danger-hover transition-colors"
            >
              <Trash2 size={14} /> Revoke link
            </button>
          </>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">
              Create a shareable link so teammates can read this conversation. They can also fork it into their own account.
            </p>

            <div>
              <label className="block text-xs font-medium text-muted-foreground mb-2">Access</label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { value: false, icon: Lock, label: 'Team only', desc: 'Signed-in users' },
                  { value: true, icon: Globe, label: 'Public', desc: 'Anyone with link' },
                ].map(opt => (
                  <button
                    key={String(opt.value)}
                    onClick={() => setPublicAccess(opt.value)}
                    className={cn(
                      'flex flex-col items-center p-3 rounded-xl border-2 text-xs transition-all',
                      publicAccess === opt.value ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground hover:border-muted-foreground/40',
                    )}
                  >
                    <opt.icon size={16} className="mb-1" />
                    <span className="font-medium">{opt.label}</span>
                    <span className="opacity-70">{opt.desc}</span>
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-muted-foreground mb-2">Expires</label>
              <div className="flex gap-2">
                {EXPIRE_OPTIONS.map(opt => (
                  <button
                    key={String(opt.value)}
                    onClick={() => setExpireDays(opt.value)}
                    className={cn(
                      'flex-1 py-2 rounded-lg border text-xs font-medium transition-colors',
                      expireDays === opt.value ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground hover:border-muted-foreground/40',
                    )}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>
          </>
        )}
      </ModalBody>

      {!link && !isLoading && (
        <ModalFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button onClick={handleCreate} loading={isCreating}>
            {isCreating ? 'Creating…' : 'Create link'}
          </Button>
        </ModalFooter>
      )}
      {link && (
        <ModalFooter>
          <Button onClick={onClose}>Done</Button>
        </ModalFooter>
      )}
    </Modal>
  );
};

export default ShareModal;
