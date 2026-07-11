import React from 'react';
import { cn } from '../../lib/cn';

const SIZES = {
  sm: 'h-3.5 w-3.5 border-2',
  md: 'h-5 w-5 border-2',
  lg: 'h-8 w-8 border-[3px]',
} as const;

export default function Spinner({ size = 'md', className }: { size?: keyof typeof SIZES; className?: string }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={cn(
        'inline-block animate-spin rounded-full border-current border-t-transparent text-primary',
        SIZES[size],
        className,
      )}
    />
  );
}
