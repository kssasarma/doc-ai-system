import React, { forwardRef } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { motion, type HTMLMotionProps } from 'framer-motion';
import { cn } from '../../lib/cn';

const iconButtonVariants = cva(
  'inline-flex items-center justify-center rounded-lg transition-colors disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        ghost: 'text-muted-foreground hover:bg-surface-hover hover:text-foreground',
        solid: 'bg-muted text-foreground hover:bg-surface-hover',
        primary: 'bg-primary text-primary-foreground hover:bg-primary-hover',
        danger: 'text-danger hover:bg-danger/10',
      },
      size: {
        sm: 'h-7 w-7',
        md: 'h-8 w-8',
        lg: 'h-9 w-9',
      },
    },
    defaultVariants: { variant: 'ghost', size: 'md' },
  },
);

export interface IconButtonProps
  extends Omit<HTMLMotionProps<'button'>, 'ref'>,
    VariantProps<typeof iconButtonVariants> {
  /** Required — every icon-only button must be reachable by name for screen-reader users. */
  label: string;
}

/** The one icon-only button used app-wide — `label` is required at the type level so an
 * accessible name can never be forgotten (renders as both `aria-label` and a `title` tooltip). */
const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ className, variant, size, label, children, ...props }, ref) => {
    return (
      <motion.button
        ref={ref}
        type="button"
        aria-label={label}
        title={label}
        whileTap={{ scale: 0.92 }}
        transition={{ duration: 0.1 }}
        className={cn(iconButtonVariants({ variant, size }), className)}
        {...props}
      >
        {children}
      </motion.button>
    );
  },
);
IconButton.displayName = 'IconButton';

export default IconButton;
