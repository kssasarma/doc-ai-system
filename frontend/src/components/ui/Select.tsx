import React, { forwardRef } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '../../lib/cn';
import { baseFieldClasses } from './Input';

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, id, children, ...props }, ref) => {
    const inputId = id ?? props.name;
    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-foreground mb-1">
            {label}
          </label>
        )}
        <div className="relative">
          <select
            ref={ref}
            id={inputId}
            className={cn(baseFieldClasses, 'appearance-none pr-9', error && 'border-danger focus:ring-danger focus:border-danger', className)}
            aria-invalid={!!error}
            {...props}
          >
            {children}
          </select>
          <ChevronDown size={15} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        </div>
        {error && <p className="text-xs text-danger mt-1">{error}</p>}
      </div>
    );
  },
);
Select.displayName = 'Select';

export default Select;
