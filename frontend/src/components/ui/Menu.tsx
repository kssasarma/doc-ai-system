import React, { Fragment } from 'react';
import { Menu as HMenu, MenuButton, MenuItems, MenuItem, Transition } from '@headlessui/react';
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
 * implementation. */
export default function Menu({
  trigger, options, align = 'end', className,
}: {
  trigger: React.ReactNode;
  options: MenuOption[];
  align?: 'start' | 'end';
  className?: string;
}) {
  return (
    <HMenu as="div" className="relative inline-block text-left">
      <MenuButton as={Fragment}>{trigger}</MenuButton>
      <Transition
        as={Fragment}
        enter="ease-out duration-150" enterFrom="opacity-0 scale-95 -translate-y-1" enterTo="opacity-100 scale-100 translate-y-0"
        leave="ease-in duration-100" leaveFrom="opacity-100 scale-100" leaveTo="opacity-0 scale-95"
      >
        <MenuItems
          className={cn(
            'absolute z-40 mt-1.5 min-w-[10rem] rounded-lg border border-border bg-surface shadow-elevated dark:shadow-elevated-dark py-1 focus:outline-none',
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
      </Transition>
    </HMenu>
  );
}
