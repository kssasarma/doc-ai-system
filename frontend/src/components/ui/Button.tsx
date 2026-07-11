import React, { forwardRef } from 'react';
import { motion, type HTMLMotionProps } from 'framer-motion';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '../../lib/cn';
import Spinner from './Spinner';

export const buttonVariants = cva(
  'inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-lg font-medium transition-colors disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-primary text-primary-foreground hover:bg-primary-hover shadow-soft',
        secondary: 'bg-muted text-foreground hover:bg-surface-hover',
        outline: 'border border-border bg-transparent text-foreground hover:bg-surface-hover',
        ghost: 'bg-transparent text-muted-foreground hover:bg-surface-hover hover:text-foreground',
        danger: 'bg-danger text-danger-foreground hover:bg-danger-hover shadow-soft',
        link: 'bg-transparent text-primary hover:underline p-0 h-auto',
      },
      size: {
        sm: 'h-8 px-3 text-xs',
        md: 'h-9 px-4 text-sm',
        lg: 'h-11 px-5 text-sm',
        icon: 'h-9 w-9 p-0',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps
  extends Omit<HTMLMotionProps<'button'>, 'ref' | 'children'>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  children?: React.ReactNode;
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, leftIcon, rightIcon, children, ...props }, ref) => {
    return (
      <motion.button
        ref={ref}
        whileTap={{ scale: 0.97 }}
        transition={{ duration: 0.1 }}
        className={cn(buttonVariants({ variant, size }), className)}
        disabled={disabled || loading}
        {...props}
      >
        {loading ? <Spinner size="sm" className={variant === 'primary' || variant === 'danger' ? 'text-current' : undefined} /> : leftIcon}
        {children}
        {!loading && rightIcon}
      </motion.button>
    );
  },
);
Button.displayName = 'Button';

export default Button;
