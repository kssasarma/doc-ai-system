import React, { createContext, useCallback, useContext, useState } from 'react';
import Modal, { ModalBody, ModalFooter } from './Modal';
import Button from './Button';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Styles the confirm button as destructive (red) — for delete/erase/revoke-style actions. */
  danger?: boolean;
}

interface PendingConfirm {
  options: ConfirmOptions;
  resolve: (confirmed: boolean) => void;
}

const ConfirmContext = createContext<((options: ConfirmOptions) => Promise<boolean>) | null>(null);

/** Promise-based confirm dialog backed by the shared `Modal` primitive — replaces `window.confirm`
 * (no theming, no focus trap, blocks the JS thread) app-wide (Phase 6.8). Usage mirrors
 * `window.confirm`'s ergonomics: `if (await confirm({ title, message })) { ... }`. */
export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [pending, setPending] = useState<PendingConfirm | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>(resolve => {
      setPending({ options, resolve });
    });
  }, []);

  const settle = (confirmed: boolean) => {
    pending?.resolve(confirmed);
    setPending(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Modal open={!!pending} onClose={() => settle(false)} title={pending?.options.title} size="sm">
        <ModalBody>
          <p className="text-sm text-muted-foreground">{pending?.options.message}</p>
        </ModalBody>
        <ModalFooter>
          <Button variant="outline" onClick={() => settle(false)}>
            {pending?.options.cancelLabel ?? 'Cancel'}
          </Button>
          <Button variant={pending?.options.danger ? 'danger' : 'primary'} onClick={() => settle(true)}>
            {pending?.options.confirmLabel ?? 'Confirm'}
          </Button>
        </ModalFooter>
      </Modal>
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): (options: ConfirmOptions) => Promise<boolean> {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm must be used inside ConfirmProvider');
  return ctx;
}
