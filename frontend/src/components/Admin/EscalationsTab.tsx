import React, { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { Escalation } from '../../types';
import { fetchEscalations, answerEscalation, updateEscalationStatus } from '../../services/escalationService';
import { HelpCircle, Send } from 'lucide-react';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Button from '../ui/Button';
import Badge, { type BadgeProps } from '../ui/Badge';
import Textarea from '../ui/Textarea';
import EmptyState from '../ui/EmptyState';

const STATUS_BADGE: Record<Escalation['status'], NonNullable<BadgeProps['variant']>> = {
  ANSWERED: 'success',
  IN_REVIEW: 'primary',
  CLOSED: 'neutral',
  PENDING: 'warning',
};

export default function EscalationsTab() {
  const { token } = useAuth();
  const [escalations, setEscalations] = useState<Escalation[]>([]);
  const [answeringId, setAnsweringId] = useState<string | null>(null);
  const [answerDraft, setAnswerDraft] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    const res = await fetchEscalations(token);
    if (res.success && res.data) setEscalations(res.data);
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handleAnswer = async (id: string) => {
    if (!token || !answerDraft.trim()) return;
    const res = await answerEscalation(id, answerDraft.trim(), token);
    if (res.success && res.data) {
      setEscalations(prev => prev.map(e => e.id === id ? res.data! : e));
      setAnsweringId(null);
      setAnswerDraft('');
    }
  };

  const handleStatus = async (id: string, status: Escalation['status']) => {
    if (!token) return;
    const res = await updateEscalationStatus(id, status, token);
    if (res.success && res.data) setEscalations(prev => prev.map(e => e.id === id ? res.data! : e));
  };

  const open = escalations.filter(e => e.status === 'PENDING' || e.status === 'IN_REVIEW').length;

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible">
      <PageHeader
        title="Expert Escalations"
        description="Questions escalated by users for expert review and answers."
        actions={<Badge variant="warning">{open} open</Badge>}
      />
      <motion.div variants={fadeInUp}>
        <Card>
          {escalations.length === 0 ? (
            <EmptyState
              icon={HelpCircle}
              title="No escalations yet"
              description="Questions escalated by users for expert review will appear here."
            />
          ) : (
            <div className="divide-y divide-border">
              {escalations.map(e => (
                <div key={e.id} className="px-6 py-4 space-y-2">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground line-clamp-2">{e.questionText}</p>
                      <div className="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
                        <span>by {e.createdByUsername}</span>
                        {e.product && <span>{e.product} {e.version}</span>}
                        <span>{new Date(e.createdAt).toLocaleDateString()}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <Badge variant={STATUS_BADGE[e.status]}>{e.status}</Badge>
                      {(e.status === 'PENDING' || e.status === 'IN_REVIEW') && (
                        <>
                          {e.status === 'PENDING' && (
                            <Button variant="outline" size="sm" onClick={() => handleStatus(e.id, 'IN_REVIEW')}>Claim</Button>
                          )}
                          <Button variant="outline" size="sm" onClick={() => { setAnsweringId(e.id); setAnswerDraft(''); }}>Answer</Button>
                          <Button variant="ghost" size="sm" onClick={() => handleStatus(e.id, 'CLOSED')}>Close</Button>
                        </>
                      )}
                    </div>
                  </div>
                  {e.expertAnswer && (
                    <div className="bg-success/10 border border-success/20 rounded-lg px-3 py-2">
                      <p className="text-xs font-medium text-success mb-0.5">Expert answer:</p>
                      <p className="text-xs text-foreground">{e.expertAnswer}</p>
                    </div>
                  )}
                  {answeringId === e.id && (
                    <div className="space-y-2">
                      <Textarea
                        rows={3}
                        value={answerDraft}
                        onChange={ev => setAnswerDraft(ev.target.value)}
                        placeholder="Type your expert answer…"
                        autoFocus
                      />
                      <div className="flex gap-2 justify-end">
                        <Button variant="ghost" size="sm" onClick={() => setAnsweringId(null)}>Cancel</Button>
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleAnswer(e.id)}
                          disabled={!answerDraft.trim()}
                          leftIcon={<Send className="w-3 h-3" />}
                        >
                          Submit answer
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>
      </motion.div>
    </motion.div>
  );
}
