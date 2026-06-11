import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Escalation } from '../../types';
import { fetchEscalations, answerEscalation, updateEscalationStatus } from '../../services/escalationService';
import { HelpCircle, Send } from 'lucide-react';

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
    <div className="bg-white rounded-xl border border-gray-200">
      <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
        <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
          <HelpCircle className="w-4 h-4 text-orange-500" />
          Expert Escalations
        </h2>
        <span className="text-sm text-gray-500">{open} open</span>
      </div>
      {escalations.length === 0 ? (
        <div className="p-12 text-center text-gray-400 text-sm">No escalations yet.</div>
      ) : (
        <div className="divide-y divide-gray-100">
          {escalations.map(e => (
            <div key={e.id} className="px-6 py-4 space-y-2">
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 line-clamp-2">{e.questionText}</p>
                  <div className="flex items-center gap-3 mt-1 text-xs text-gray-500">
                    <span>by {e.createdByUsername}</span>
                    {e.product && <span>{e.product} {e.version}</span>}
                    <span>{new Date(e.createdAt).toLocaleDateString()}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                    e.status === 'ANSWERED' ? 'bg-green-100 text-green-700'
                    : e.status === 'IN_REVIEW' ? 'bg-blue-100 text-blue-700'
                    : e.status === 'CLOSED' ? 'bg-gray-100 text-gray-500'
                    : 'bg-orange-100 text-orange-700'
                  }`}>
                    {e.status}
                  </span>
                  {(e.status === 'PENDING' || e.status === 'IN_REVIEW') && (
                    <>
                      {e.status === 'PENDING' && (
                        <button onClick={() => handleStatus(e.id, 'IN_REVIEW')} className="text-xs text-blue-600 hover:underline">Claim</button>
                      )}
                      <button onClick={() => { setAnsweringId(e.id); setAnswerDraft(''); }} className="text-xs text-orange-600 hover:underline">Answer</button>
                      <button onClick={() => handleStatus(e.id, 'CLOSED')} className="text-xs text-gray-400 hover:underline">Close</button>
                    </>
                  )}
                </div>
              </div>
              {e.expertAnswer && (
                <div className="bg-green-50 border border-green-100 rounded-lg px-3 py-2">
                  <p className="text-xs font-medium text-green-700 mb-0.5">Expert answer:</p>
                  <p className="text-xs text-gray-700">{e.expertAnswer}</p>
                </div>
              )}
              {answeringId === e.id && (
                <div className="space-y-2">
                  <textarea rows={3} value={answerDraft} onChange={ev => setAnswerDraft(ev.target.value)}
                    placeholder="Type your expert answer…" autoFocus
                    className="w-full px-3 py-2 text-sm border border-orange-200 rounded-xl resize-none focus:outline-none focus:ring-2 focus:ring-orange-400" />
                  <div className="flex gap-2 justify-end">
                    <button onClick={() => setAnsweringId(null)} className="px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
                    <button onClick={() => handleAnswer(e.id)} disabled={!answerDraft.trim()}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-orange-500 text-white rounded-lg hover:bg-orange-600 disabled:opacity-50 transition-colors">
                      <Send className="w-3 h-3" /> Submit answer
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
