import React, { Fragment } from 'react';
import { Dialog, DialogPanel, DialogTitle, Transition, TransitionChild } from '@headlessui/react';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';
import IconButton from './IconButton';

const SIZES = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
} as const;

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  icon?: React.ReactNode;
  size?: keyof typeof SIZES;
  children: React.ReactNode;
  className?: string;
}

/** The one modal shell used everywhere — real focus trap, Escape-to-close, click-outside-to-close,
 * and `aria-modal`/`DialogTitle` wiring come from Headless UI's `Dialog` for free (none of the
 * hand-rolled modals this replaced had any of that). Animation uses Headless UI's own
 * Transition/TransitionChild (CSS-class driven) rather than framer-motion here specifically,
 * since fighting AnimatePresence against Dialog's own portal/focus-trap unmount timing is a
 * known source of bugs — framer-motion is still used for everything inside the panel's content. */
export default function Modal({ open, onClose, title, icon, size = 'md', children, className }: ModalProps) {
  return (
    <Transition show={open} as={Fragment}>
      <Dialog onClose={onClose} className="relative z-50">
        <TransitionChild
          as={Fragment}
          enter="ease-out duration-200" enterFrom="opacity-0" enterTo="opacity-100"
          leave="ease-in duration-150" leaveFrom="opacity-100" leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm" aria-hidden="true" />
        </TransitionChild>

        <div className="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild
            as={Fragment}
            enter="ease-out duration-200" enterFrom="opacity-0 scale-95 translate-y-2" enterTo="opacity-100 scale-100 translate-y-0"
            leave="ease-in duration-150" leaveFrom="opacity-100 scale-100 translate-y-0" leaveTo="opacity-0 scale-95 translate-y-1"
          >
            <DialogPanel className={cn('w-full rounded-2xl bg-surface border border-border shadow-elevated dark:shadow-elevated-dark max-h-[85vh] overflow-y-auto', SIZES[size], className)}>
              {title && (
                <div className="sticky top-0 z-10 bg-surface flex items-center justify-between px-6 py-4 border-b border-border">
                  <DialogTitle className="flex items-center gap-2 text-base font-semibold text-foreground">
                    {icon}
                    {title}
                  </DialogTitle>
                  <IconButton label="Close" variant="ghost" size="sm" onClick={onClose}>
                    <X size={16} />
                  </IconButton>
                </div>
              )}
              {children}
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </Transition>
  );
}

export function ModalBody({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-6', className)} {...props} />;
}

export function ModalFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex items-center justify-end gap-2 px-6 py-4 border-t border-border', className)} {...props} />;
}
