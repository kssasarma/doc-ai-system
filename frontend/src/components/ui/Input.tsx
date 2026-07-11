import React, { forwardRef } from 'react';
import { cn } from '../../lib/cn';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
}

const baseFieldClasses =
  'w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary disabled:opacity-50 disabled:cursor-not-allowed';

export { baseFieldClasses };

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const inputId = id ?? props.name;
    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-foreground mb-1">
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={cn(baseFieldClasses, error && 'border-danger focus:ring-danger focus:border-danger', className)}
          aria-invalid={!!error}
          {...props}
        />
        {error ? (
          <p className="text-xs text-danger mt-1">{error}</p>
        ) : hint ? (
          <p className="text-xs text-muted-foreground mt-1">{hint}</p>
        ) : null}
      </div>
    );
  },
);
Input.displayName = 'Input';

export default Input;
