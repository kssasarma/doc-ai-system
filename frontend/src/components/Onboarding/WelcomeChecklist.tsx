import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle2, Circle, Rocket, X, ArrowRight } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { fetchIngestionStatus } from '../../services/adminService';
import { listGroups } from '../../services/groupService';
import { getTenantUsers } from '../../services/tenantService';
import { fadeInUp } from '../../lib/motion';
import { cn } from '../../lib/cn';
import IconButton from '../ui/IconButton';

const DISMISS_KEY_PREFIX = 'docai_onboarding_dismissed_';

interface Step {
  key: string;
  label: string;
  done: boolean;
  to: string;
}

/**
 * A real, derived checklist — not a static "seen it" tutorial. Each step reflects actual tenant
 * state (documents uploaded, a group created, a teammate invited), so it stays accurate even if
 * the admin does these steps from elsewhere in the app rather than by clicking through here.
 * Shown only to tenant ADMINs, only until all three steps are true or the admin dismisses it.
 */
export default function WelcomeChecklist() {
  const { user, token } = useAuth();
  const navigate = useNavigate();
  const [steps, setSteps] = useState<Step[] | null>(null);
  const [dismissed, setDismissed] = useState(true);

  useEffect(() => {
    if (!user || user.role !== 'ADMIN') return;
    setDismissed(localStorage.getItem(DISMISS_KEY_PREFIX + user.userId) === '1');
  }, [user]);

  useEffect(() => {
    if (!token || !user || user.role !== 'ADMIN' || dismissed) return;
    Promise.all([
      fetchIngestionStatus(token).catch(() => null),
      listGroups(token).catch(() => []),
      user.tenantId
        ? getTenantUsers(token, user.tenantId, { size: 2 }).catch(() => ({ content: [], totalElements: 0, totalPages: 0 }))
        : Promise.resolve({ content: [], totalElements: 0, totalPages: 0 }),
    ]).then(([status, groups, tenantUsers]) => {
      setSteps([
        { key: 'upload', label: 'Upload your first document', done: (status?.totalDocuments ?? 0) > 0, to: '/admin/documents' },
        { key: 'access', label: 'Create a group and grant it access', done: groups.length > 0, to: '/admin/groups' },
        { key: 'invite', label: 'Invite your team', done: tenantUsers.totalElements > 1, to: '/admin/users' },
      ]);
    });
  }, [token, user, dismissed]);

  if (!user || user.role !== 'ADMIN' || dismissed || !steps) return null;
  if (steps.every(s => s.done)) return null;

  const completedCount = steps.filter(s => s.done).length;

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY_PREFIX + user.userId, '1');
    setDismissed(true);
  };

  return (
    <AnimatePresence>
      <motion.div
        variants={fadeInUp}
        initial="hidden"
        animate="visible"
        exit={{ opacity: 0, height: 0, marginBottom: 0 }}
        className="mb-6 rounded-xl border border-primary/20 bg-primary/5 overflow-hidden"
      >
        <div className="flex items-start gap-3 p-5">
          <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Rocket size={18} className="text-primary" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between gap-2">
              <div>
                <h3 className="text-sm font-semibold text-foreground">Get your workspace ready</h3>
                <p className="text-xs text-muted-foreground mt-0.5">{completedCount} of {steps.length} steps complete</p>
              </div>
              <IconButton label="Dismiss checklist" variant="ghost" size="sm" onClick={dismiss}>
                <X size={15} />
              </IconButton>
            </div>

            <div className="mt-3 space-y-1.5">
              {steps.map(step => (
                <button
                  key={step.key}
                  onClick={() => !step.done && navigate(step.to)}
                  disabled={step.done}
                  className={cn(
                    'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-sm transition-colors group',
                    step.done ? 'text-muted-foreground' : 'text-foreground hover:bg-primary/10 cursor-pointer',
                  )}
                >
                  {step.done ? (
                    <CheckCircle2 size={16} className="text-success flex-shrink-0" />
                  ) : (
                    <Circle size={16} className="text-muted-foreground flex-shrink-0" />
                  )}
                  <span className={cn('flex-1', step.done && 'line-through')}>{step.label}</span>
                  {!step.done && (
                    <ArrowRight size={14} className="text-primary opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
                  )}
                </button>
              ))}
            </div>
          </div>
        </div>
      </motion.div>
    </AnimatePresence>
  );
}
