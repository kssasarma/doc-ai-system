import React, { Fragment } from 'react';
import { Menu as HMenu, MenuButton, MenuItems, MenuItem } from '@headlessui/react';
import { cn } from '../../lib/cn';

export interface MenuOption {
  key: string;
  label: string;
  icon?: React.ReactNode;
  onSelect: () => void;
  danger?: boolean;
  disabled?: boolean;
}

/** Accessible, animated dropdown menu — replaces the app's hand-rolled dropdowns (manual
 * outside-click listeners, CSS-hover-only menus) with one consistent, keyboard-navigable
 * implementation.
 *
 * Uses `MenuItems`' own built-in `transition` prop (data-[closed]/data-[enter]/data-[leave]
 * attributes) rather than wrapping it in a separate `<Transition as={Fragment}>` — Headless UI v2
 * warns that pattern can fail to forward a ref to the underlying DOM node. */
export default function Menu({
  trigger, options, align = 'end', placement = 'bottom', className,
}: {
  trigger: React.ReactNode;
  options: MenuOption[];
  align?: 'start' | 'end';
  /** 'top' opens upward from the trigger — use for triggers pinned to the bottom of the
   * viewport (e.g. a sidebar footer), where a downward menu would get clipped. */
  placement?: 'top' | 'bottom';
  className?: string;
}) {
  return (
    <HMenu as="div" className="relative inline-block text-left">
      <MenuButton as={Fragment}>{trigger}</MenuButton>
      <MenuItems
        transition
        className={cn(
          'absolute z-40 min-w-[10rem] rounded-lg border border-border bg-surface shadow-elevated dark:shadow-elevated-dark py-1 focus:outline-none',
          'transition duration-150 ease-out data-[closed]:opacity-0 data-[closed]:scale-95',
          placement === 'top'
            ? 'bottom-full mb-1.5 data-[closed]:translate-y-1'
            : 'top-full mt-1.5 data-[closed]:-translate-y-1',
          align === 'end' ? 'right-0' : 'left-0',
          className,
        )}
      >
        {options.map(opt => (
          <MenuItem key={opt.key} disabled={opt.disabled}>
            {({ focus }) => (
              <button
                type="button"
                onClick={opt.onSelect}
                disabled={opt.disabled}
                className={cn(
                  'w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors disabled:opacity-50',
                  opt.danger ? 'text-danger' : 'text-foreground',
                  focus && (opt.danger ? 'bg-danger/10' : 'bg-surface-hover'),
                )}
              >
                {opt.icon}
                {opt.label}
              </button>
            )}
          </MenuItem>
        ))}
      </MenuItems>
    </HMenu>
  );
}
