import React, { Suspense, lazy, useState } from 'react';
import { Popover, PopoverButton, PopoverPanel, Transition } from '@headlessui/react';
import { Filter, X, ChevronDown, GitCompare } from 'lucide-react';
import { ProductEntry } from '../../types';
import { fetchAccessibleProducts } from '../../services/productService';
import { useAuth } from '../../context/AuthContext';
import { cn } from '../../lib/cn';

const VersionCompareModal = lazy(() => import('./VersionCompareModal'));

interface ScopeChipProps {
  product?: string;
  version?: string;
  onChange: (product: string | undefined, version: string | undefined) => void;
}

/**
 * Optional, per-conversation retrieval scope — defaults to "everything I have access to" and
 * never blocks sending a message. Pinning a product/version here narrows what's searchable for
 * this chat (see SearchScope.withVersionNarrow on the backend); it never widens it beyond the
 * user's actual access grants.
 */
export default function ScopeChip({ product, version, onChange }: ScopeChipProps) {
  const { token } = useAuth();
  const [products, setProducts] = useState<ProductEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [compareTarget, setCompareTarget] = useState<ProductEntry | null>(null);

  const isPinned = !!(product || version);
  const label = isPinned ? `${product ?? 'Any product'}${version ? ` ${version}` : ''}` : 'All my documents';

  const loadProducts = () => {
    if (products.length > 0 || !token) return;
    setLoading(true);
    setError(false);
    fetchAccessibleProducts(token)
      .then(setProducts)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  };

  return (
    <>
    <Popover className="relative flex-shrink-0">
      {({ close }) => {
        const select = (p: string | undefined, v: string | undefined) => {
          onChange(p, v);
          close();
        };
        return (
          <>
            <PopoverButton
              onClick={loadProducts}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg transition-colors border focus:outline-none',
                isPinned
                  ? 'text-primary bg-primary/10 border-primary/20'
                  : 'text-muted-foreground hover:text-foreground hover:bg-surface-hover border-border',
              )}
              title="Scope this conversation to a specific product/version"
            >
              <Filter size={14} />
              <span className="max-w-[160px] truncate">{label}</span>
              <ChevronDown size={12} />
            </PopoverButton>

            <Transition
              enter="ease-out duration-150" enterFrom="opacity-0 scale-95 -translate-y-1" enterTo="opacity-100 scale-100 translate-y-0"
              leave="ease-in duration-100" leaveFrom="opacity-100 scale-100" leaveTo="opacity-0 scale-95"
            >
              <PopoverPanel className="absolute left-0 top-full mt-1.5 bg-surface border border-border rounded-lg shadow-elevated dark:shadow-elevated-dark py-1 z-20 w-64 max-h-80 overflow-y-auto">
                <button
                  onClick={() => select(undefined, undefined)}
                  className={cn(
                    'w-full text-left px-3 py-2 text-sm hover:bg-surface-hover transition-colors',
                    !isPinned ? 'font-medium text-primary' : 'text-foreground',
                  )}
                >
                  All my documents (default)
                </button>
                <div className="border-t border-border my-1" />
                {loading ? (
                  <div className="px-3 py-3 text-xs text-muted-foreground text-center">Loading…</div>
                ) : error ? (
                  <div className="px-3 py-3 text-xs text-danger text-center">Couldn't load products</div>
                ) : products.length === 0 ? (
                  <div className="px-3 py-3 text-xs text-muted-foreground text-center">No documents available yet</div>
                ) : (
                  products.map(p => (
                    <div key={p.product}>
                      <div className="px-3 py-1 flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wide truncate">
                          {p.product}
                        </span>
                        {p.versions.length >= 2 && (
                          <button
                            onClick={() => { setCompareTarget(p); close(); }}
                            title={`Compare ${p.product} versions`}
                            aria-label={`Compare ${p.product} versions`}
                            className="p-0.5 text-muted-foreground hover:text-primary flex-shrink-0"
                          >
                            <GitCompare size={12} />
                          </button>
                        )}
                      </div>
                      <button
                        onClick={() => select(p.product, undefined)}
                        className={cn(
                          'w-full text-left pl-5 pr-3 py-1.5 text-sm hover:bg-surface-hover transition-colors',
                          product === p.product && !version ? 'font-medium text-primary' : 'text-foreground',
                        )}
                      >
                        All versions
                      </button>
                      {p.versions.map(v => (
                        <button
                          key={v}
                          onClick={() => select(p.product, v)}
                          className={cn(
                            'w-full text-left pl-5 pr-3 py-1.5 text-sm hover:bg-surface-hover transition-colors',
                            product === p.product && version === v ? 'font-medium text-primary' : 'text-foreground',
                          )}
                        >
                          {v}
                        </button>
                      ))}
                    </div>
                  ))
                )}
                {isPinned && (
                  <>
                    <div className="border-t border-border my-1" />
                    <button
                      onClick={() => select(undefined, undefined)}
                      className="w-full text-left px-3 py-2 text-sm text-danger hover:bg-danger/10 transition-colors flex items-center gap-1.5"
                    >
                      <X size={13} /> Clear pin
                    </button>
                  </>
                )}
              </PopoverPanel>
            </Transition>
          </>
        );
      }}
    </Popover>

    {compareTarget && (
      <Suspense fallback={null}>
        <VersionCompareModal
          product={compareTarget.product}
          versions={compareTarget.versions}
          onClose={() => setCompareTarget(null)}
        />
      </Suspense>
    )}
    </>
  );
}
