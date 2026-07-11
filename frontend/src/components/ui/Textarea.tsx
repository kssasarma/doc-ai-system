import React, { forwardRef } from 'react';
import { cn } from '../../lib/cn';
import { baseFieldClasses } from './Input';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
}

const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const inputId = id ?? props.name;
    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-foreground mb-1">
            {label}
          </label>
        )}
        <textarea
          ref={ref}
          id={inputId}
          className={cn(baseFieldClasses, 'resize-none', error && 'border-danger focus:ring-danger focus:border-danger', className)}
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
Textarea.displayName = 'Textarea';

export default Textarea;
