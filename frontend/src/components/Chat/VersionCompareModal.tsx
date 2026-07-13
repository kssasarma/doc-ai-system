import React, { useState } from 'react';
import { GitCompare, History } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import {
  getVersionDiff, getEvolutionTimeline,
  VersionDiffResult, EvolutionTimeline,
} from '../../services/intelligenceService';
import Modal, { ModalBody } from '../ui/Modal';
import Button from '../ui/Button';
import Input from '../ui/Input';
import Select from '../ui/Select';
import { cn } from '../../lib/cn';
import { useToast } from '../ui/Toast';

interface VersionCompareModalProps {
  product: string;
  versions: string[];
  onClose: () => void;
}

type Mode = 'diff' | 'evolution';
type Tone = 'success' | 'warning' | 'danger' | 'accent';

const TONE_CLASSES: Record<Tone, string> = {
  success: 'border-success/25 bg-success/5 text-success',
  warning: 'border-warning/25 bg-warning/5 text-warning',
  danger: 'border-danger/25 bg-danger/5 text-danger',
  accent: 'border-accent/25 bg-accent/5 text-accent',
};

function DiffSection({ label, content, tone }: { label: string; content: string | null; tone: Tone }) {
  if (!content || content.trim().toLowerCase() === 'none') return null;
  return (
    <div className={cn('border rounded-lg p-3', TONE_CLASSES[tone])}>
      <h4 className="text-xs font-semibold uppercase tracking-wide mb-1">{label}</h4>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{content}</p>
    </div>
  );
}

export default function VersionCompareModal({ product, versions, onClose }: VersionCompareModalProps) {
  const { token } = useAuth();
  const toast = useToast();
  const [mode, setMode] = useState<Mode>('diff');

  const [topic, setTopic] = useState('');
  const [versionA, setVersionA] = useState(versions[0] ?? '');
  const [versionB, setVersionB] = useState(versions[1] ?? versions[0] ?? '');
  const [diffResult, setDiffResult] = useState<VersionDiffResult | null>(null);

  const [question, setQuestion] = useState('');
  const [timeline, setTimeline] = useState<EvolutionTimeline | null>(null);

  const [loading, setLoading] = useState(false);

  const runDiff = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !topic.trim()) return;
    setLoading(true);
    setDiffResult(null);
    const res = await getVersionDiff(token, topic.trim(), product, versionA, versionB);
    if (res.success && res.data) setDiffResult(res.data);
    else toast.error(res.error ?? 'Failed to compare versions.');
    setLoading(false);
  };

  const runEvolution = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !question.trim()) return;
    setLoading(true);
    setTimeline(null);
    const res = await getEvolutionTimeline(token, question.trim(), product);
    if (res.success && res.data) setTimeline(res.data);
    else toast.error(res.error ?? 'Failed to build evolution timeline.');
    setLoading(false);
  };

  return (
    <Modal open onClose={onClose} size="xl" title={`${product} — Version Intelligence`} icon={<GitCompare size={18} className="text-primary flex-shrink-0" />}>
      <div className="sticky top-[57px] z-10 bg-surface flex border-b border-border">
        <button
          onClick={() => setMode('diff')}
          className={cn(
            'flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
            mode === 'diff' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground',
          )}
        >
          <GitCompare size={14} /> Compare two versions
        </button>
        <button
          onClick={() => setMode('evolution')}
          className={cn(
            'flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
            mode === 'evolution' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground',
          )}
        >
          <History size={14} /> Evolution timeline
        </button>
      </div>

      <ModalBody>
        {mode === 'diff' ? (
          <>
            <form onSubmit={runDiff} className="space-y-3 mb-5">
              <Input label="Topic" value={topic} onChange={e => setTopic(e.target.value)} required autoFocus placeholder="e.g. SSO configuration" />
              <div className="grid grid-cols-2 gap-3">
                <Select label="From version" value={versionA} onChange={e => setVersionA(e.target.value)}>
                  {versions.map(v => <option key={v} value={v}>{v}</option>)}
                </Select>
                <Select label="To version" value={versionB} onChange={e => setVersionB(e.target.value)}>
                  {versions.map(v => <option key={v} value={v}>{v}</option>)}
                </Select>
              </div>
              <Button type="submit" loading={loading} disabled={!topic.trim()} leftIcon={!loading ? <GitCompare size={14} /> : undefined}>
                {loading ? 'Comparing…' : 'Compare'}
              </Button>
            </form>

            {diffResult && (
              <div className="space-y-3">
                <p className="text-sm text-foreground bg-primary/5 border border-primary/15 rounded-lg p-3">{diffResult.summary}</p>
                <DiffSection label="Added" content={diffResult.added} tone="success" />
                <DiffSection label="Modified" content={diffResult.modified} tone="warning" />
                <DiffSection label="Removed" content={diffResult.removed} tone="danger" />
                <DiffSection label="Breaking changes" content={diffResult.breakingChanges} tone="accent" />
              </div>
            )}
          </>
        ) : (
          <>
            <form onSubmit={runEvolution} className="space-y-3 mb-5">
              <Input label="Question" value={question} onChange={e => setQuestion(e.target.value)} required autoFocus placeholder="e.g. How do I configure SSO?" />
              <Button type="submit" loading={loading} disabled={!question.trim()} leftIcon={!loading ? <History size={14} /> : undefined}>
                {loading ? 'Building timeline…' : `Show across all ${versions.length} versions`}
              </Button>
            </form>

            {timeline && (
              <div className="space-y-3">
                {timeline.breakingSummary && (
                  <p className="text-sm text-accent bg-accent/5 border border-accent/15 rounded-lg p-3">
                    {timeline.breakingSummary}
                  </p>
                )}
                {timeline.snapshots.map(snap => (
                  <div key={snap.version} className="border border-border rounded-lg p-3">
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-sm font-semibold text-foreground">{snap.version}</span>
                      {snap.hasDocumentation ? (
                        <span className="text-xs text-muted-foreground">{Math.round(snap.confidence * 100)}% match</span>
                      ) : (
                        <span className="text-xs text-muted-foreground italic">No documentation found</span>
                      )}
                    </div>
                    {snap.answer && <p className="text-sm text-muted-foreground leading-relaxed">{snap.answer}</p>}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </ModalBody>
    </Modal>
  );
}
