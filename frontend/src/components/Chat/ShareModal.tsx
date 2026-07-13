import React, { useEffect, useState } from 'react';
import { Share2, Copy, Check, Trash2, Globe, Lock, Pencil, UserPlus, Users } from 'lucide-react';
import { ShareLink, ShareRecipient, TenantUser } from '../../types';
import {
  createShareLink,
  updateShareLink,
  getShareLink,
  deleteShareLink,
  listRecipients,
  addRecipient,
  removeRecipient,
} from '../../services/shareService';
import { getTenantUsers } from '../../services/tenantService';
import { useAuth } from '../../context/AuthContext';
import Modal, { ModalBody, ModalFooter } from '../ui/Modal';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Select from '../ui/Select';
import Spinner from '../ui/Spinner';
import { cn } from '../../lib/cn';
import { useToast } from '../ui/Toast';

interface ShareModalProps {
  chatId: string;
  onClose: () => void;
}

const EXPIRE_OPTIONS = [
  { label: '7 days', value: 7 },
  { label: '30 days', value: 30 },
  { label: 'Never', value: null },
];

const ACCESS_OPTIONS = [
  { value: false, icon: Lock, label: 'Workspace', desc: 'People in your workspace' },
  { value: true, icon: Globe, label: 'Public', desc: 'Anyone with link' },
];

const ShareModal: React.FC<ShareModalProps> = ({ chatId, onClose }) => {
  const { token, user } = useAuth();
  const toast = useToast();
  const [link, setLink] = useState<ShareLink | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [copied, setCopied] = useState(false);
  const [editing, setEditing] = useState(false);
  const [publicAccess, setPublicAccess] = useState(false);
  const [expireDays, setExpireDays] = useState<number | null>(7);

  const [recipients, setRecipients] = useState<ShareRecipient[]>([]);
  const [tenantUsers, setTenantUsers] = useState<TenantUser[]>([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [addingRecipient, setAddingRecipient] = useState(false);
  const [removingRecipientId, setRemovingRecipientId] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    getShareLink(chatId, token).then(res => {
      if (res.success && res.data) setLink(res.data);
    }).finally(() => setIsLoading(false));
  }, [chatId, token]);

  const loadRecipients = React.useCallback(() => {
    if (!token || !link) return;
    listRecipients(chatId, token).then(res => {
      if (res.success && res.data) setRecipients(res.data);
    });
  }, [chatId, token, link]);

  useEffect(() => { loadRecipients(); }, [loadRecipients]);

  useEffect(() => {
    if (!token || !user?.tenantId) return;
    getTenantUsers(token, user.tenantId).then(setTenantUsers).catch(() => {});
  }, [token, user?.tenantId]);

  const shareUrl = link
    ? `${window.location.origin}/share/${link.token}`
    : '';

  const startEditing = () => {
    setPublicAccess(link?.publicAccess ?? false);
    setExpireDays(7);
    setEditing(true);
  };

  const handleSave = async () => {
    if (!token) return;
    setIsSaving(true);
    const res = link
      ? await updateShareLink(chatId, publicAccess, expireDays, token)
      : await createShareLink(chatId, publicAccess, expireDays, token);
    if (res.success && res.data) {
      setLink(res.data);
      setEditing(false);
      toast.success(link ? 'Share settings updated.' : 'Share link created.');
    } else {
      toast.error(res.error ?? 'Failed to save share settings.');
    }
    setIsSaving(false);
  };

  const handleDelete = async () => {
    if (!token) return;
    const res = await deleteShareLink(chatId, token);
    if (res.success) {
      setLink(null);
      setRecipients([]);
      setEditing(false);
      toast.success('Share link revoked.');
    } else {
      toast.error(res.error ?? 'Failed to revoke share link.');
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(shareUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleAddRecipient = async () => {
    if (!token || !selectedUserId) return;
    setAddingRecipient(true);
    const res = await addRecipient(chatId, selectedUserId, token);
    if (res.success) {
      setSelectedUserId('');
      loadRecipients();
      setLink(l => l ? { ...l, recipientCount: l.recipientCount + 1 } : l);
      toast.success('Access granted.');
    } else {
      toast.error(res.error ?? 'Failed to grant access.');
    }
    setAddingRecipient(false);
  };

  const handleRemoveRecipient = async (userId: string) => {
    if (!token) return;
    setRemovingRecipientId(userId);
    const res = await removeRecipient(chatId, userId, token);
    if (res.success) {
      loadRecipients();
      setLink(l => l ? { ...l, recipientCount: Math.max(0, l.recipientCount - 1) } : l);
      toast.success('Access revoked.');
    } else {
      toast.error(res.error ?? 'Failed to revoke access.');
    }
    setRemovingRecipientId(null);
  };

  const showSettingsForm = editing || !link;
  const grantedIds = new Set(recipients.map(r => r.userId));
  const grantableUsers = tenantUsers.filter(u => !grantedIds.has(u.userId));

  return (
    <Modal open onClose={onClose} title="Share Chat" icon={<Share2 size={18} className="text-primary" />}>
      <ModalBody className="space-y-4">
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <Spinner size="lg" />
          </div>
        ) : (
          <>
            {link && !editing && (
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
                    : link.recipientCount > 0
                      ? <><Users size={14} className="text-muted-foreground" /> Only {link.recipientCount} specific {link.recipientCount === 1 ? 'person' : 'people'} can view</>
                      : <><Lock size={14} className="text-muted-foreground" /> Only people in your workspace can view</>
                  }
                  {link.expiresAt && (
                    <span className="ml-auto text-xs">
                      Expires {new Date(link.expiresAt).toLocaleDateString()}
                    </span>
                  )}
                </div>

                {!link.publicAccess && (
                  <div className="space-y-2 pt-2 border-t border-border">
                    <label className="block text-xs font-medium text-muted-foreground">
                      Share with specific people
                      <span className="font-normal opacity-80"> (optional — narrows access down from the whole workspace)</span>
                    </label>
                    <div className="flex gap-2">
                      <div className="flex-1">
                        <Select value={selectedUserId} onChange={e => setSelectedUserId(e.target.value)} aria-label="Select a person to share with">
                          <option value="">
                            {grantableUsers.length === 0 ? 'No more people to add' : 'Select a person…'}
                          </option>
                          {grantableUsers.map(u => (
                            <option key={u.userId} value={u.userId}>{u.username} ({u.email})</option>
                          ))}
                        </Select>
                      </div>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={handleAddRecipient}
                        disabled={!selectedUserId}
                        loading={addingRecipient}
                        leftIcon={!addingRecipient ? <UserPlus size={13} /> : undefined}
                      >
                        Add
                      </Button>
                    </div>
                    {recipients.length > 0 && (
                      <div className="space-y-1">
                        {recipients.map(r => (
                          <div key={r.userId} className="flex items-center justify-between bg-muted border border-border rounded-lg px-3 py-1.5">
                            <span className="text-xs text-foreground">{r.username}</span>
                            <IconButton
                              label={`Remove ${r.username}'s access`}
                              variant="danger"
                              size="sm"
                              onClick={() => handleRemoveRecipient(r.userId)}
                              disabled={removingRecipientId === r.userId}
                            >
                              <Trash2 size={13} />
                            </IconButton>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                <div className="flex items-center gap-4">
                  <button
                    onClick={startEditing}
                    className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
                  >
                    <Pencil size={14} /> Edit settings
                  </button>
                  <button
                    onClick={handleDelete}
                    className="flex items-center gap-2 text-sm text-danger hover:text-danger-hover transition-colors"
                  >
                    <Trash2 size={14} /> Revoke link
                  </button>
                </div>
              </>
            )}

            {showSettingsForm && (
              <>
                {!link && (
                  <p className="text-sm text-muted-foreground">
                    Create a shareable link so teammates can read this conversation. They can also fork it into their own account.
                  </p>
                )}

                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-2">Access</label>
                  <div className="grid grid-cols-2 gap-2">
                    {ACCESS_OPTIONS.map(opt => (
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
                  {link && (
                    <p className="text-xs text-muted-foreground mt-1.5">Expiration resets to this choice, counted from now.</p>
                  )}
                </div>
              </>
            )}
          </>
        )}
      </ModalBody>

      {showSettingsForm && !isLoading && (
        <ModalFooter>
          <Button variant="ghost" onClick={link ? () => setEditing(false) : onClose}>Cancel</Button>
          <Button onClick={handleSave} loading={isSaving}>
            {isSaving ? 'Saving…' : link ? 'Save changes' : 'Create link'}
          </Button>
        </ModalFooter>
      )}
      {link && !editing && (
        <ModalFooter>
          <Button onClick={onClose}>Done</Button>
        </ModalFooter>
      )}
    </Modal>
  );
};

export default ShareModal;
