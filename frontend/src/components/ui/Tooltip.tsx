import React, { useState, useId } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { cn } from '../../lib/cn';

export default function Tooltip({
  content, children, side = 'top', className,
}: {
  content: string;
  children: React.ReactElement;
  side?: 'top' | 'bottom' | 'left' | 'right';
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const id = useId();

  const positionClasses = {
    top: 'bottom-full left-1/2 -translate-x-1/2 mb-1.5',
    bottom: 'top-full left-1/2 -translate-x-1/2 mt-1.5',
    left: 'right-full top-1/2 -translate-y-1/2 mr-1.5',
    right: 'left-full top-1/2 -translate-y-1/2 ml-1.5',
  }[side];

  return (
    <span
      className="relative inline-flex"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
    >
      {React.cloneElement(children, { 'aria-describedby': open ? id : undefined })}
      <AnimatePresence>
        {open && (
          <motion.span
            role="tooltip"
            id={id}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            transition={{ duration: 0.12 }}
            className={cn(
              'absolute z-50 whitespace-nowrap rounded-md bg-foreground text-background px-2 py-1 text-xs font-medium shadow-elevated pointer-events-none',
              positionClasses,
              className,
            )}
          >
            {content}
          </motion.span>
        )}
      </AnimatePresence>
    </span>
  );
}
