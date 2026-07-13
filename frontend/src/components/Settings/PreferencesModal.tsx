import React, { useEffect, useState } from 'react';
import { SlidersHorizontal, Check, Download, Trash2 } from 'lucide-react';
import { UserPreference } from '../../types';
import { fetchPreferences, savePreferences } from '../../services/userPreferenceService';
import { exportMyData, requestAccountDeletion } from '../../services/gdprService';
import { useAuth } from '../../context/AuthContext';
import Modal, { ModalBody, ModalFooter } from '../ui/Modal';
import Button from '../ui/Button';
import Spinner from '../ui/Spinner';
import { useToast } from '../ui/Toast';
import { cn } from '../../lib/cn';

interface PreferencesModalProps {
  onClose: () => void;
}

const VERBOSITY_OPTIONS: { value: UserPreference['verbosity']; label: string; desc: string }[] = [
  { value: 'CONCISE', label: 'Concise', desc: '2–3 sentence answers' },
  { value: 'BALANCED', label: 'Balanced', desc: 'Default — clear and complete' },
  { value: 'DETAILED', label: 'Detailed', desc: 'Full explanation with examples' },
];

const FORMAT_OPTIONS: { value: UserPreference['answerFormat']; label: string; desc: string }[] = [
  { value: 'PROSE', label: 'Prose', desc: 'Flowing paragraphs (default)' },
  { value: 'BULLET_POINTS', label: 'Bullet Points', desc: 'Structured list format' },
  { value: 'CODE_FIRST', label: 'Code First', desc: 'Lead with code, then explain' },
];

const optionButtonClasses = (active: boolean) =>
  cn(
    'flex flex-col items-center p-3 rounded-xl border-2 text-xs transition-all',
    active ? 'border-primary bg-primary/10 text-primary' : 'border-border text-muted-foreground hover:border-muted-foreground/40',
  );

const PreferencesModal: React.FC<PreferencesModalProps> = ({ onClose }) => {
  const { token } = useAuth();
  const toast = useToast();
  const [prefs, setPrefs] = useState<UserPreference>({
    verbosity: 'BALANCED',
    answerFormat: 'PROSE',
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [isRequestingDeletion, setIsRequestingDeletion] = useState(false);

  useEffect(() => {
    if (!token) return;
    fetchPreferences(token).then(res => {
      if (res.success && res.data) setPrefs(res.data);
    }).finally(() => setIsLoading(false));
  }, [token]);

  const handleSave = async () => {
    if (!token) return;
    setIsSaving(true);
    const res = await savePreferences(token, prefs);
    if (res.success) {
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } else {
      toast.error(res.error ?? 'Failed to save preferences.');
    }
    setIsSaving(false);
  };

  const handleExport = async () => {
    if (!token) return;
    setIsExporting(true);
    const res = await exportMyData(token);
    if (res.success) {
      toast.success('Your data export has started downloading.');
    } else {
      toast.error(res.error ?? 'Failed to export your data.');
    }
    setIsExporting(false);
  };

  const handleRequestDeletion = async () => {
    if (!token) return;
    if (!window.confirm(
      'Request deletion of your account and personal data? An admin will process this request; it cannot be undone once completed.'
    )) return;
    setIsRequestingDeletion(true);
    const res = await requestAccountDeletion(token);
    if (res.success) {
      toast.success('Deletion request submitted. An admin will process it shortly.');
    } else {
      toast.error(res.error ?? 'Failed to submit deletion request.');
    }
    setIsRequestingDeletion(false);
  };

  return (
    <Modal open onClose={onClose} title="Preferences" icon={<SlidersHorizontal size={18} className="text-primary" />}>
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Spinner size="lg" />
        </div>
      ) : (
        <ModalBody className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">Answer verbosity</label>
            <div className="grid grid-cols-3 gap-2">
              {VERBOSITY_OPTIONS.map(opt => (
                <button key={opt.value} onClick={() => setPrefs(p => ({ ...p, verbosity: opt.value }))} className={optionButtonClasses(prefs.verbosity === opt.value)}>
                  <span className="font-medium mb-0.5">{opt.label}</span>
                  <span className="text-center opacity-70 leading-tight">{opt.desc}</span>
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-foreground mb-2">Answer format</label>
            <div className="grid grid-cols-3 gap-2">
              {FORMAT_OPTIONS.map(opt => (
                <button key={opt.value} onClick={() => setPrefs(p => ({ ...p, answerFormat: opt.value }))} className={optionButtonClasses(prefs.answerFormat === opt.value)}>
                  <span className="font-medium mb-0.5">{opt.label}</span>
                  <span className="text-center opacity-70 leading-tight">{opt.desc}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="pt-2 border-t border-border">
            <label className="block text-sm font-medium text-foreground mb-2">Privacy & data</label>
            <div className="flex flex-col gap-2">
              <Button
                variant="outline"
                size="sm"
                className="justify-start"
                onClick={handleExport}
                loading={isExporting}
                leftIcon={<Download size={14} />}
              >
                Export my data
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="justify-start text-danger hover:bg-danger/10 hover:border-danger/30"
                onClick={handleRequestDeletion}
                loading={isRequestingDeletion}
                leftIcon={<Trash2 size={14} />}
              >
                Request account deletion
              </Button>
            </div>
          </div>
        </ModalBody>
      )}

      <ModalFooter>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button onClick={handleSave} disabled={isSaving || isLoading} leftIcon={saved ? <Check size={14} /> : undefined}>
          {saved ? 'Saved' : isSaving ? 'Saving…' : 'Save preferences'}
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default PreferencesModal;
