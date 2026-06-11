import React, { useState } from 'react';
import { X, HelpCircle, Send, Check } from 'lucide-react';
import { createEscalation } from '../../services/escalationService';
import { useAuth } from '../../context/AuthContext';

interface EscalationModalProps {
  messageId: string;
  questionText: string;
  aiAnswerText: string;
  product?: string;
  version?: string;
  onClose: () => void;
}

const EscalationModal: React.FC<EscalationModalProps> = ({
  messageId, questionText, aiAnswerText, product, version, onClose
}) => {
  const { token } = useAuth();
  const [additionalContext, setAdditionalContext] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!token) return;
    setIsSubmitting(true);
    setError(null);

    const fullQuestion = additionalContext.trim()
      ? `${questionText}\n\nAdditional context: ${additionalContext.trim()}`
      : questionText;

    const res = await createEscalation(messageId, fullQuestion, aiAnswerText, product, version, token);
    if (res.success) {
      setSubmitted(true);
      setTimeout(onClose, 2000);
    } else {
      setError(res.error || 'Failed to submit. The question may already be escalated.');
    }
    setIsSubmitting(false);
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <HelpCircle size={18} className="text-orange-500" />
            <h2 className="text-base font-semibold text-gray-900">Ask an Expert</h2>
          </div>
          <button onClick={onClose} className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
            <X size={16} />
          </button>
        </div>

        <div className="px-6 py-5 space-y-4">
          {submitted ? (
            <div className="flex flex-col items-center gap-3 py-6 text-center">
              <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center">
                <Check size={24} className="text-green-600" />
              </div>
              <p className="font-medium text-gray-900">Escalation submitted!</p>
              <p className="text-sm text-gray-500">
                An expert will review your question and respond. You'll be notified in the notification center.
              </p>
            </div>
          ) : (
            <>
              <div className="bg-gray-50 rounded-xl p-3">
                <p className="text-xs font-medium text-gray-500 mb-1">Your question</p>
                <p className="text-sm text-gray-800 line-clamp-3">{questionText}</p>
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">
                  Additional context <span className="text-gray-400 font-normal">(optional)</span>
                </label>
                <textarea
                  rows={3}
                  placeholder="Provide more details to help the expert give a precise answer…"
                  value={additionalContext}
                  onChange={e => setAdditionalContext(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-gray-300 rounded-xl resize-none focus:outline-none focus:ring-2 focus:ring-orange-400"
                />
              </div>

              <p className="text-xs text-gray-500">
                Your question and the AI's answer will be sent to a product expert for review. You'll receive a notification when they respond.
              </p>

              {error && <p className="text-xs text-red-500">{error}</p>}
            </>
          )}
        </div>

        {!submitted && (
          <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100">
            <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              Cancel
            </button>
            <button
              onClick={handleSubmit}
              disabled={isSubmitting}
              className="flex items-center gap-2 px-4 py-2 text-sm bg-orange-500 text-white rounded-lg hover:bg-orange-600 disabled:opacity-50 transition-colors font-medium"
            >
              <Send size={13} />
              {isSubmitting ? 'Submitting…' : 'Submit to expert'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default EscalationModal;
