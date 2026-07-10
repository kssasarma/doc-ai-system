import React, { useEffect, useState } from 'react';
import { X, SlidersHorizontal, Check } from 'lucide-react';
import { UserPreference } from '../../types';
import { fetchPreferences, savePreferences } from '../../services/userPreferenceService';
import { useAuth } from '../../context/AuthContext';

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

const PreferencesModal: React.FC<PreferencesModalProps> = ({ onClose }) => {
  const { token } = useAuth();
  const [prefs, setPrefs] = useState<UserPreference>({
    verbosity: 'BALANCED',
    answerFormat: 'PROSE',
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);

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
    }
    setIsSaving(false);
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <SlidersHorizontal size={18} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Preferences</h2>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
          </div>
        ) : (
          <div className="px-6 py-5 space-y-6">
            {/* Verbosity */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Answer verbosity</label>
              <div className="grid grid-cols-3 gap-2">
                {VERBOSITY_OPTIONS.map(opt => (
                  <button
                    key={opt.value}
                    onClick={() => setPrefs(p => ({ ...p, verbosity: opt.value }))}
                    className={`flex flex-col items-center p-3 rounded-xl border-2 text-xs transition-all ${
                      prefs.verbosity === opt.value
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300'
                    }`}
                  >
                    <span className="font-medium mb-0.5">{opt.label}</span>
                    <span className="text-center opacity-70 leading-tight">{opt.desc}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Format */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Answer format</label>
              <div className="grid grid-cols-3 gap-2">
                {FORMAT_OPTIONS.map(opt => (
                  <button
                    key={opt.value}
                    onClick={() => setPrefs(p => ({ ...p, answerFormat: opt.value }))}
                    className={`flex flex-col items-center p-3 rounded-xl border-2 text-xs transition-all ${
                      prefs.answerFormat === opt.value
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300'
                    }`}
                  >
                    <span className="font-medium mb-0.5">{opt.label}</span>
                    <span className="text-center opacity-70 leading-tight">{opt.desc}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={isSaving || isLoading}
            className="flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors font-medium"
          >
            {saved ? (
              <><Check size={14} /> Saved</>
            ) : isSaving ? (
              'Saving…'
            ) : (
              'Save preferences'
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default PreferencesModal;
