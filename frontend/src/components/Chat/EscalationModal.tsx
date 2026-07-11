import React, { useState } from 'react';
import { HelpCircle, Send, Check } from 'lucide-react';
import { createEscalation } from '../../services/escalationService';
import { useAuth } from '../../context/AuthContext';
import Modal, { ModalBody, ModalFooter } from '../ui/Modal';
import Button from '../ui/Button';
import Textarea from '../ui/Textarea';

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
    <Modal open onClose={onClose} title="Ask an Expert" icon={<HelpCircle size={18} className="text-warning" />}>
      <ModalBody className="space-y-4">
        {submitted ? (
          <div className="flex flex-col items-center gap-3 py-6 text-center">
            <div className="w-12 h-12 bg-success/10 rounded-full flex items-center justify-center">
              <Check size={24} className="text-success" />
            </div>
            <p className="font-medium text-foreground">Escalation submitted!</p>
            <p className="text-sm text-muted-foreground">
              An expert will review your question and respond. You'll be notified in the notification center.
            </p>
          </div>
        ) : (
          <>
            <div className="bg-muted rounded-xl p-3">
              <p className="text-xs font-medium text-muted-foreground mb-1">Your question</p>
              <p className="text-sm text-foreground line-clamp-3">{questionText}</p>
            </div>

            <Textarea
              label="Additional context (optional)"
              rows={3}
              placeholder="Provide more details to help the expert give a precise answer…"
              value={additionalContext}
              onChange={e => setAdditionalContext(e.target.value)}
            />

            <p className="text-xs text-muted-foreground">
              Your question and the AI's answer will be sent to a product expert for review. You'll receive a notification when they respond.
            </p>

            {error && <p className="text-xs text-danger">{error}</p>}
          </>
        )}
      </ModalBody>

      {!submitted && (
        <ModalFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSubmit} loading={isSubmitting} leftIcon={!isSubmitting ? <Send size={13} /> : undefined}>
            {isSubmitting ? 'Submitting…' : 'Submit to expert'}
          </Button>
        </ModalFooter>
      )}
    </Modal>
  );
};

export default EscalationModal;
