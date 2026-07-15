import React, { forwardRef, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '../../lib/cn';
import { baseFieldClasses } from './Input';

export interface PasswordInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  /** Renders a live strength meter below the field, scored from the current value. Only makes
   * sense for a "new password" field — never enable this on a login/current-password field, since
   * scoring an existing password provides no useful signal and could be read as second-guessing it. */
  showStrength?: boolean;
}

/** Score 0-4. Deliberately simple (length + character-class diversity) rather than pulling in a
 * dictionary/entropy library — good enough to steer users away from the obviously-weak end
 * without pretending to be a real strength estimator. */
export function scorePasswordStrength(password: string): number {
  if (!password) return 0;
  let score = 0;
  if (password.length >= 10) score++;
  if (password.length >= 14) score++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  return Math.min(score, 4);
}

const STRENGTH_LABELS = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
const STRENGTH_COLORS = ['bg-danger', 'bg-danger', 'bg-warning', 'bg-primary', 'bg-success'];

const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  ({ className, label, error, hint, id, showStrength, value, ...props }, ref) => {
    const [visible, setVisible] = useState(false);
    const inputId = id ?? props.name;
    const strength = showStrength ? scorePasswordStrength(String(value ?? '')) : null;

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-foreground mb-1">
            {label}
          </label>
        )}
        <div className="relative">
          <input
            ref={ref}
            id={inputId}
            type={visible ? 'text' : 'password'}
            value={value}
            className={cn(baseFieldClasses, 'pr-10', error && 'border-danger focus:ring-danger focus:border-danger', className)}
            aria-invalid={!!error}
            {...props}
          />
          <button
            type="button"
            onClick={() => setVisible(v => !v)}
            className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
            aria-label={visible ? 'Hide password' : 'Show password'}
            tabIndex={-1}
          >
            {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        </div>
        {showStrength && value ? (
          <div className="mt-1.5">
            <div className="flex gap-1">
              {[0, 1, 2, 3].map(i => (
                <div
                  key={i}
                  className={cn('h-1 flex-1 rounded-full bg-border', (strength ?? 0) > i && STRENGTH_COLORS[strength ?? 0])}
                />
              ))}
            </div>
            <p className="text-xs text-muted-foreground mt-1">{STRENGTH_LABELS[strength ?? 0]}</p>
          </div>
        ) : null}
        {error ? (
          <p className="text-xs text-danger mt-1">{error}</p>
        ) : hint ? (
          <p className="text-xs text-muted-foreground mt-1">{hint}</p>
        ) : null}
      </div>
    );
  },
);
PasswordInput.displayName = 'PasswordInput';

export default PasswordInput;
